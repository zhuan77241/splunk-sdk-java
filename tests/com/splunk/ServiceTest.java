/*
 * Copyright 2011 Splunk, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"): you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.splunk;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.net.Socket;
import org.junit.Test;


public class ServiceTest extends SplunkTestCase {
    // Perform some non-intrusive inspection of the given Job object.
    void checkJob(Job job) {
        // There may be a race condition between a job creation and job being
        // ready. If we hit it, checking any job attribute will raise a splunk
        // Exception of job-not-ready. So keep retrying after 500 ms.
        while (true) {
            try {
                job.getCursorTime();
                break;
            } catch (SplunkException e) {
                sleep(500);
                continue;
            }
        }
        job.getDelegate();
        job.getDiskUsage();
        job.getDispatchState();
        job.getDoneProgress();
        job.getDropCount();
        job.getEarliestTime();
        job.getEventAvailableCount();
        job.getEventCount();
        job.getEventFieldCount();
        job.getEventIsStreaming();
        job.getEventIsTruncated();
        job.getEventSearch();
        job.getEventSorting();
        job.getKeywords();
        job.getLabel();
        job.getLatestTime();
        job.getNumPreviews();
        job.getPriority();
        job.getRemoteSearch();
        job.getReportSearch();
        job.getResultCount();
        job.getResultIsStreaming();
        job.getResultPreviewCount();
        job.getRunDuration();
        job.getScanCount();
        job.getSearch();
        job.getSearchEarliestTime();
        job.getSearchLatestTime();
        job.getSid();
        job.getStatusBuckets();
        job.getTtl();
        job.isDone();
        job.isFailed();
        job.isFinalized();
        job.isPaused();
        job.isPreviewEnabled();
        job.isRealTimeSearch();
        job.isSaved();
        job.isSavedSearch();
        job.isZombie();
        assertEquals(job.getName(), job.getSid());
    }

    void checkResponse(ResponseMessage response) {
        assertEquals(200, response.getStatus());
        try {
            // Make sure we can at least load the Atom response
            AtomFeed feed = AtomFeed.parse(response.getContent());
        }
        catch (Exception e) {
            fail(e.getMessage());
        }
    }

    boolean contains(String[] array, String value) {
        for (int i = 0; i < array.length; ++i)
            if (array[i].equals(value)) return true;
        return false;
    }

    @Test public void testCapabilities() throws Exception {
        Service service = connect();

        List<String> expected = Arrays.asList(
            "admin_all_objects", "change_authentication",
            "change_own_password", "delete_by_keyword",
            "edit_deployment_client", "edit_deployment_server",
            "edit_dist_peer", "edit_forwarders", "edit_httpauths",
            "edit_input_defaults", "edit_monitor", "edit_roles",
            "edit_scripted", "edit_search_server", "edit_server",
            "edit_splunktcp", "edit_splunktcp_ssl", "edit_tcp", "edit_udp",
            "edit_user", "edit_web_settings", "get_metadata",
            "get_typeahead", "indexes_edit", "license_edit", "license_tab",
            "list_deployment_client", "list_forwarders", "list_httpauths",
            "list_inputs", "request_remote_tok", "rest_apps_management",
            "rest_apps_view", "rest_properties_get", "rest_properties_set",
            "restart_splunkd", "rtsearch", "schedule_search", "search",
            "use_file_operator");

        String[] caps = service.getCapabilities();
        for (String name : expected)
            assertTrue(contains(caps, name));
    }

    // Make a few simple requests and make sure the results look ok.
    @Test public void testGet() {
        Service service = connect();

        // Check a few paths that we know exist
        String[] paths = { 
            "/", 
            "/services", 
            "/services/search/jobs",
            "search/jobs",
            "authentication/users"
        };

        for (String path : paths)
            checkResponse(service.get(path));

        // And make sure we get the expected 404
        try {
            ResponseMessage response = service.get("/zippy");
            fail("Expected HttpException");
        }
        catch (HttpException e) {
            assertEquals(e.getStatus(), 404);
        }
    }

    @Test public void testInfo() throws Exception {
        Service service = connect();

        List <String> expected = Arrays.asList(
            "build", "cpu_arch", "guid", "isFree", "isTrial", "licenseKeys",
            "licenseSignature", "licenseState", "master_guid", "mode",
            "os_build", "os_name", "os_version", "serverName", "version");

        ServiceInfo info = service.getInfo();
        for (String name : expected)
            assertTrue(info.containsKey(name));

        info.getBuild();
        info.getCpuArch();
        info.getGuid();
        info.getLicenseKeys();
        info.getLicenseLabels();
        info.getLicenseSignature();
        info.getLicenseState();
        info.getMasterGuid();
        info.getMode();
        info.getOsBuild();
        info.getOsName();
        info.getOsVersion();
        info.getServerName();
        info.getVersion();
        info.isFree();
        info.isRtSearchEnabled();
        info.isTrial();
    }

    @Test public void testLogin() {
        ResponseMessage response;

        Service service = new Service(
            command.host, 
            command.port, 
            command.scheme);

        // Not logged in, should fail with 401
        try {
            response = service.get("/services/authentication/users");
            fail("Expected HttpException");
        }
        catch (HttpException e) {
            assertEquals(e.getStatus(), 401);
        }

        // Logged in, request should succeed
        service.login(command.username, command.password);
        response = service.get("/services/authentication/users");
        checkResponse(response);

        // Logout, the request should fail with a 401
        service.logout();
        try {
            response = service.get("/services/authentication/users");
            fail("Expected HttpException");
        }
        catch (HttpException e) {
            assertEquals(e.getStatus(), 401);
        }
    }

    @Test public void testNamespace() {
        Service service = connect();
        HashMap<String, String> namespace = new HashMap<String, String>();

        // synctactic tests
        namespace.clear();
        assertTrue(service.fullpath("", null).equals("/services/"));

        namespace.clear();
        namespace.put("owner", "Bob");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/Bob/-/"));

        namespace.clear();
        namespace.put("app", "search");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/-/search/"));

        namespace.clear();
        namespace.put("app", "search");
        namespace.put("owner", "Bob");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/Bob/search/"));

        namespace.clear();
        namespace.put("sharing", "user");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/-/-/"));

        namespace.clear();
        namespace.put("sharing", "user");
        namespace.put("owner", "Bob");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/Bob/-/"));

        namespace.clear();
        namespace.put("sharing", "user");
        namespace.put("app", "search");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/-/search/"));

        namespace.clear();
        namespace.put("sharing", "user");
        namespace.put("app", "search");
        namespace.put("owner", "Bob");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/Bob/search/"));

        namespace.clear();
        namespace.put("sharing", "app");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/nobody/-/"));

        namespace.clear();
        namespace.put("sharing", "app");
        namespace.put("owner", "Bob");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/nobody/-/"));

        namespace.clear();
        namespace.put("sharing", "app");
        namespace.put("app", "search");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/nobody/search/"));

        namespace.clear();
        namespace.put("sharing", "app");
        namespace.put("app", "search");
        namespace.put("owner", "Bob");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/nobody/search/"));

        namespace.clear();
        namespace.put("sharing", "global");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/nobody/-/"));

        namespace.clear();
        namespace.put("sharing", "global");
        namespace.put("owner", "Bob");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/nobody/-/"));

        namespace.clear();
        namespace.put("sharing", "global");
        namespace.put("app", "search");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/nobody/search/"));

        namespace.clear();
        namespace.put("sharing", "global");
        namespace.put("app", "search");
        namespace.put("owner", "Bob");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/nobody/search/"));

        namespace.clear();
        namespace.put("sharing", "system");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/nobody/system/"));

        namespace.clear();
        namespace.put("sharing", "system");
        namespace.put("owner", "Bob");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/nobody/system/"));

        namespace.clear();
        namespace.put("sharing", "system");
        namespace.put("app", "search");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/nobody/system/"));

        namespace.clear();
        namespace.put("sharing", "system");
        namespace.put("app", "search");
        namespace.put("owner", "Bob");
        assertTrue(service.fullpath("", namespace)
                .equals("/servicesNS/nobody/system/"));
    }

    @Test public void testRestart() throws Exception {

        boolean restarted = false;

        Service service = connect();

        ResponseMessage response = service.restart();
        assertEquals(200, response.getStatus());

        // port sniff. expect connection ... then no connection ...
        // the connection. Max 3 minutes.

        int totalTime = 0;
        // server up, wait until socket no longer accepted.
        while (totalTime < (3*60*1000)) {
            try {
                Socket ServerSok = new Socket(service.getHost(),service.getPort());
			    ServerSok.close();
			    Thread.sleep(10); // 10 milliseconds
                totalTime += 10;
    		}
            catch (Exception e) {
                break;
		    }
        }

        // server down, wait until socket accepted.
        while (totalTime < (3*60*1000)) {
            try {
                Socket ServerSok = new Socket(service.getHost(),service.getPort());
			    ServerSok.close();
                break;

    		}
            catch (Exception e) {
			    Thread.sleep(10); // 10 milliseconds
                totalTime += 10;
		    }
        }

        while (totalTime < (3*60*1000)) {
            try {
                connect();
                restarted = true;
                break;
            }
            catch (Exception e) {
                // server not back yet
                Thread.sleep(100);
                totalTime += 10;
            }
        }
        assertTrue(restarted);
    }

    @Test public void testJobs() {
        Job job;

        Service service = connect();

        JobCollection jobs = service.getJobs();
        for (Entity entity : jobs.values())
            checkJob((Job)entity);

        job = jobs.create("search *");
        checkJob(job);
        job.cancel();
    }

    @Test public void testUsers() {
        Args args;
        User user;

        Service service = connect();

        String username = "sdk-user";
        String password = "changeme";

        UserCollection users = service.getUsers();

        // Cleanup potential prior failed test run.
        users.remove(username);
        assertFalse(users.containsKey(username));

        // Create user using base create method
        args = new Args();
        args.put("password", password);
        args.put("roles", "power");
        user = users.create(username, args);
        assertTrue(users.containsKey(username));
        assertEquals(user.getName(), username);
        assertTrue(user.getRoles().length == 1);
        assertTrue(contains(user.getRoles(), "power"));

        users.remove(username);
        assertFalse(users.containsKey(username));

        // Create user using derived create method 
        user = users.create(username, password, "power");
        assertTrue(users.containsKey(username));
        assertEquals(user.getName(), username);
        assertTrue(user.getRoles().length == 1);
        assertTrue(contains(user.getRoles(), "power"));

        users.remove(username);
        assertFalse(users.containsKey(username));

        // Create using derived method with multiple roles
        user = users.create(
            username, password, new String[] { "power", "user" });
        assertTrue(users.containsKey(username));
        assertEquals(user.getName(), username);
        assertTrue(user.getRoles().length == 2);
        assertTrue(contains(user.getRoles(), "power"));
        assertTrue(contains(user.getRoles(), "user"));

        users.remove(username);
        assertFalse(users.containsKey(username));

        // Create using drived method with multiple roles and extra properties
        args = new Args();
        args.put("realname", "Renzo");
        args.put("email", "email.me@now.com");
        args.put("defaultApp", "search");
        user = users.create(
            username, password, new String[] { "power", "user" }, args);
        assertTrue(users.containsKey(username));
        assertEquals(user.getName(), username);
        assertTrue(user.getRoles().length == 2);
        assertTrue(contains(user.getRoles(), "power"));
        assertTrue(contains(user.getRoles(), "user"));
        assertEquals(user.getRealName(), "Renzo");
        assertEquals(user.getEmail(), "email.me@now.com");
        assertEquals(user.getDefaultApp(), "search");
        user.getTz();

        users.remove(username);
        assertFalse(users.containsKey(username));
    }
}

