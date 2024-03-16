package io.github.chains_project.dependency_privileges.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.chains_project.dependency_privileges.detectorCategories.AbstractCategory;

import java.util.List;

public class AccessRecord {

    private final String dependency;
    private List<DetectorEvent> records;

    /**
     * Create a new AccessRecord object that is a record of all resource accesses for a specific dependency.
     *
     * @param dependency the dependency
     * @param records    a list of runtime accesses
     */
    @JsonCreator
    public AccessRecord(@JsonProperty("dependency") String dependency,
                        @JsonProperty("records") List<DetectorEvent> records) {
        this.dependency = dependency;
        this.records = records;
    }

    public List<DetectorEvent> getRecords() {
        return records;
    }

    public String getDependency() {
        return dependency;
    }

    public void setRecords(List<DetectorEvent> records) {
        this.records = records;
    }

    @Override
    public String toString() {
        return ("AccessRecord{dependency = %s, records = %s}")
                .formatted(dependency, records.toString());
    }

    public static class DetectorEvent {
        private final String detectorCategory;
        private final List<AbstractCategory> events;

        /**
         * Create a new DetectorEvent object that is a record of specific resource access.
         *
         * @param detectorCategory event category name
         * @param events           a list of detector category objects for the specific event
         *                         and for the specific method with information on runtime accesses
         */
        @JsonCreator
        public DetectorEvent(@JsonProperty("detectorCategory") String detectorCategory,
                             @JsonProperty("accesses") List<AbstractCategory> events) {
            this.detectorCategory = detectorCategory;
            this.events = events;
        }

        public String getDetectorCategory() {
            return detectorCategory;
        }

        public List<AbstractCategory> getEvents() {
            return events;
        }

        @Override
        public String toString() {
            return ("DetectorEvent{detectorCategory = %s, events = %s}")
                    .formatted(detectorCategory, events.toString());
        }
    }

}
