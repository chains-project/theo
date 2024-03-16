package io.github.chains_project.dependency_privileges.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

public class DependencyParserUtils {

    private final Map<String, Object> lockfileData;

    public DependencyParserUtils(String lockfilePath) {
        try {
            this.lockfileData = parseLockfile(lockfilePath);
        } catch (IOException e) {
            throw new RuntimeException("Lockfile could not be found. Check the README.md for more details", e);
        }
    }

    private Map<String, Object> parseLockfile(String lockfilePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        File lockfile = new File(lockfilePath);
        return objectMapper.readValue(lockfile, Map.class);
    }

    public String findDepDetails(String jarPath) {
        if (jarPath != null) {
            String[] parts = extractDepDetails(jarPath);
            if (parts != null) {
                return findDepDetailsRecursively(lockfileData.get("dependencies"), parts[0], parts[1], parts[2]);
            }
        }
        return null;
    }

    private String[] extractDepDetails(String jarPath) {
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

    private String findDepDetailsRecursively(Object node, String groupId, String artifactId, String version) {
        if (node instanceof Iterable) {
            for (Object child : (Iterable<?>) node) {
                String result = findDepDetailsRecursively(child, groupId, artifactId, version);
                if (result != null) {
                    return result; // Return the result if found
                }
            }
        } else if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            if (map.containsKey("groupId") && map.containsKey("artifactId") && map.containsKey("version")) {
                String depGroupId = (String) map.get("groupId");
                String depArtifactId = (String) map.get("artifactId");
                String depVersion = (String) map.get("version");
                if (depGroupId.contains(groupId) && depArtifactId.equals(artifactId) && depVersion.equals(version)) {
                    return (depGroupId + ":" + depArtifactId + ":" + depVersion);
                }
            }
        }
        return null;
    }

}
