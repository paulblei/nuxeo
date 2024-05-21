/*
 * (C) Copyright 2023 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Guillaume Renard
 */

package org.nuxeo.ecm.restapi.server.management;

import static org.junit.Assert.assertEquals;
import static org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants.ALL_WORKFLOWS_QUERY;
import static org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants.DOCUMENT_ROUTE_DOCUMENT_TYPE;
import static org.nuxeo.ecm.platform.task.TaskConstants.TASK_PROCESS_ID_PROPERTY_NAME;
import static org.nuxeo.ecm.platform.task.TaskConstants.TASK_TYPE_NAME;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.platform.audit.AuditFeature;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.test.WorkflowFeature;
import org.nuxeo.ecm.restapi.test.ManagementBaseTest;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 2023
 */
@Features({ AutomationFeature.class, WorkflowFeature.class, AuditFeature.class })
@Deploy("org.nuxeo.ecm.platform.restapi.server.routing")
public class TestWorkflowsObject extends ManagementBaseTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected BulkService bulkService;

    protected int nbWorkspaces = 2;

    protected int nbRoutes = 5;

    protected int nbTasksPerRoute = 2;

    protected int nbFilesPerRoute = 2;

    protected List<DocumentRef> workspaces;

    protected DocumentModelList getDocumentRoutes() {
        return session.query(ALL_WORKFLOWS_QUERY);
    }

    protected DocumentModelList getRoutingTaks() {
        return session.query("Select * From " + TASK_TYPE_NAME);
    }

    @Before
    public void createDocuments() {
        workspaces = new ArrayList<>();
        for (int i = 0; i < nbWorkspaces; i++) {
            DocumentModel ws = session.createDocumentModel("/", "ws" + i, "Workspace");
            ws = session.createDocument(ws);
            workspaces.add(ws.getRef());
            for (int j = 0; j < nbRoutes; j++) {
                DocumentModel route = session.createDocumentModel("/", "dummyRoute" + i + "-" + j,
                        DOCUMENT_ROUTE_DOCUMENT_TYPE);
                var ids = new ArrayList<String>(nbFilesPerRoute);
                for (int l = 0; l < nbFilesPerRoute; l++) {
                    DocumentModel file = session.createDocumentModel("/ws" + i, "file" + i + "-" + j + "-" + l, "File");
                    file = session.createDocument(file);
                    ids.add(file.getId());
                }
                route.setPropertyValue(DocumentRoutingConstants.ATTACHED_DOCUMENTS_PROPERTY_NAME, ids);
                route = session.createDocument(route);
                for (int k = 0; k < nbTasksPerRoute; k++) {
                    DocumentModel task = session.createDocumentModel("/", i + "dummyTask" + j + "-" + k,
                            TASK_TYPE_NAME);
                    task.setPropertyValue(TASK_PROCESS_ID_PROPERTY_NAME, route.getId());
                    task = session.createDocument(task);
                }
            }
        }
        session.save();
        txFeature.nextTransaction();
    }

    @Test
    public void testGCOrphanRoutes() throws InterruptedException {
        DocumentModelList rs = getDocumentRoutes();
        DocumentModelList rts = getRoutingTaks();

        // all routes and tasks found
        assertEquals(nbWorkspaces * nbRoutes, rs.size());
        assertEquals(nbWorkspaces * nbRoutes * nbTasksPerRoute, rts.size());

        doGCRoutes(true, rs.size(), 0, rs.size());

        // all routes and tasks still found
        assertEquals(nbWorkspaces * nbRoutes, getDocumentRoutes().size());
        assertEquals(nbWorkspaces * nbRoutes * nbTasksPerRoute, getRoutingTaks().size());

        // delete 1st workspace
        session.removeDocument(workspaces.get(0));
        session.save();
        txFeature.nextTransaction();

        doGCRoutes(true, rs.size(), 0, rs.size());
        rs = getDocumentRoutes();
        rts = getRoutingTaks();
        // half of the routes and tasks found
        assertEquals(nbWorkspaces * nbRoutes / 2, rs.size());
        assertEquals(nbWorkspaces * nbRoutes * nbTasksPerRoute / 2, rts.size());

        // delete 2nd workspace
        session.removeDocument(workspaces.get(1));
        session.save();
        txFeature.nextTransaction();

        doGCRoutes(true, rs.size(), 0, rs.size());

        // Nothing left
        assertEquals(0, getDocumentRoutes().size());
        assertEquals(0, getRoutingTaks().size());

    }

    protected void doGCRoutes(boolean success, int processed, int errorCount, int total) throws InterruptedException {
        String commandId = httpClient.buildDeleteRequest("/management/workflows/orphaned")
                                     .executeAndThen(new JsonNodeHandler(), node -> {
                                         assertBulkStatusScheduled(node);
                                         return getBulkCommandId(node);
                                     });

        bulkService.await(commandId, Duration.ofMinutes(1));

        BulkStatus status = bulkService.getStatus(commandId);
        assertEquals(status.toString(), BulkStatus.State.COMPLETED, status.getState());
        assertEquals(status.toString(), !success, status.hasError());
        assertEquals(status.toString(), processed, status.getProcessed());
        assertEquals(status.toString(), errorCount, status.getErrorCount());
        assertEquals(status.toString(), total, status.getTotal());

        // Required for visibility
        txFeature.nextTransaction();
    }

}
