package io.github.chains_project.dependency_privileges.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

public class DependencyParserUtils {

    private final Map<String, Object> lockfileData;

    public DependencyParserUtils(String lockfilePath) throws IOException {
        this.lockfileData = parseLockfile(lockfilePath);
    }

    private Map<String, Object> parseLockfile(String lockfilePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        File lockfile = new File(lockfilePath);
        return objectMapper.readValue(lockfile, Map.class);
    }

    public void addAccessPrivileges(String jarPath, HashSet<String> accessPrivileges) {
        if (jarPath != null) {
            String[] parts = extractGroupIdArtifactIdVersion(jarPath);
            if (parts != null) {
                addAccessPrivilegesRecursively(lockfileData.get("dependencies"), parts[0], parts[1], parts[2],
                        accessPrivileges);
            }
        }
    }

    private String[] extractGroupIdArtifactIdVersion(String jarPath) {
        String[] parts = jarPath.split("/");
        if (parts.length >= 7) {
            String version = parts[parts.length - 2];
            String artifactId = parts[parts.length - 3];
            // ToDo: improve this approximation.
            String groupId = parts[parts.length - 4];
            return new String[]{groupId, artifactId, version};
        }
        return null;
    }

    private void addAccessPrivilegesRecursively(Object node, String groupId, String artifactId, String version,
                                                HashSet<String> accessPriviledges) {
        if (node instanceof Iterable) {
            for (Object child : (Iterable<?>) node) {
                addAccessPrivilegesRecursively(child, groupId, artifactId, version, accessPriviledges);
            }
        } else if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;

            if (map.containsKey("children")) {
                addAccessPrivilegesRecursively(map.get("children"), groupId, artifactId, version, accessPriviledges);
            }

            if (map.containsKey("groupId") && map.containsKey("artifactId") && map.containsKey("version")) {
                String depGroupId = (String) map.get("groupId");
                String depArtifactId = (String) map.get("artifactId");
                String depVersion = (String) map.get("version");

                if (depGroupId.contains(groupId) && depArtifactId.equals(artifactId) && depVersion.equals(version)) {
                    map.put("accessPrivileges", accessPriviledges);
                }
            }
        }
    }

    public void saveUpdatedLockfile(String outputFilePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFilePath), lockfileData);
    }
}
