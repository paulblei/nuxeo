/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Thomas Roger
 */

package org.nuxeo.ecm.csv.core;

import java.io.Serializable;

/**
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @since 5.7
 */
public class CSVImportStatus implements Serializable {

    public enum State {
        SCHEDULED, RUNNING, COMPLETED,
        /**
         * @since 9.1
         */
        ERROR
    }

    private static final long serialVersionUID = 1L;

    private final State state;

    private long totalNumberOfDocument;

    private long numberOfProcessedDocument;

    public CSVImportStatus(State state) {
        this(state, -1L, -1L);
    }

    public CSVImportStatus(State state, long numberOfProcessedDocument, long totalNumberOfDocument) {
        this.state = state;
        this.numberOfProcessedDocument = numberOfProcessedDocument;
        this.totalNumberOfDocument = totalNumberOfDocument;
    }

    /**
     * @since 9.1
     */
    public long getNumberOfProcessedDocument() {
        return numberOfProcessedDocument;
    }

    public State getState() {
        return state;
    }

    /**
     * @since 9.1
     */
    public long getTotalNumberOfDocument() {
        return totalNumberOfDocument;
    }

    public boolean isComplete() {
        return state == State.COMPLETED;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public boolean isScheduled() {
        return state == State.SCHEDULED;
    }
}
