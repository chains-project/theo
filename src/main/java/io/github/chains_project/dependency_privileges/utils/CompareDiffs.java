package io.github.chains_project.dependency_privileges.utils;

import org.json.JSONObject;
import org.json.JSONException;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CompareDiffs {
    public static void main(String[] args) {
        try {
            // Read JSON files
            String jsonFile1 = new String(Files.readAllBytes(Paths.get("file1.json")));
            String jsonFile2 = new String(Files.readAllBytes(Paths.get("file2.json")));

            JSONObject jsonObject1 = new JSONObject(jsonFile1);
            JSONObject jsonObject2 = new JSONObject(jsonFile2);

            Set<String> uniqueKeysFile1 = new HashSet<>();
            jsonObject1.keySet().forEach(key -> {
                if (!jsonObject2.has(key)) {
                    uniqueKeysFile1.add(key);
                }
            });

            Set<String> uniqueKeysFile2 = new HashSet<>();
            jsonObject2.keySet().forEach(key -> {
                if (!jsonObject1.has(key)) {
                    uniqueKeysFile2.add(key);
                }
            });

            Set<String> differingKeys = new HashSet<>();
            jsonObject1.keySet().forEach(key -> {
                if (jsonObject2.has(key)) {
                    Object value1 = jsonObject1.get(key);
                    Object value2 = jsonObject2.get(key);
                    if (!value1.equals(value2)) {
                        differingKeys.add(key);
                    }
                }
            });

            System.out.println("Keys unique to file1: " + uniqueKeysFile1);
            System.out.println("Keys unique to file2: " + uniqueKeysFile2);
            System.out.println("Keys with differing values: " + differingKeys);

        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }
}

//
////__________
//// Iterate through the existingList to check for matching classname and method
//boolean found = false;
//                    for (Object obj : existingList) {
//        if (obj instanceof Map) {
//Map<String, Object> frameMap = (Map<String, Object>) obj;
//Object existingMethod = frameMap.get("method");
//Object existingClassName = frameMap.get("className");
////                            System.out.println(existingMethod + "--=" + existingClassName + "--=" + method + "-" + className);
//                            try {
//                                    if (existingMethod != null && existingClassName != null && existingMethod.equals(method) && existingClassName.equals(className)) {
//// Matching classname and method found, update accesses map
//@SuppressWarnings("unchecked")
//Map<String, Map<String, String>> existingAccesses = (Map<String, Map<String, String>>) frameMap.get("accesses");
//                                    existingAccesses.putAll(accesses);
//                                    existingList.remove(frameMap);
//// Update the frameMap object
//                                    frameMap.put("accesses", existingAccesses);
//                                    existingList.add(frameMap);
//found = true;
//        break;
//        }
//        } catch (Exception e) {
//        System.err.println("check this " + e);
//                            }
//                                    }
//                                    }
//                                    if (!found) {
//Map<String, Object> frameMap = new HashMap<>();
//                        frameMap.put("method", method);
//                        frameMap.put("className", className);
//                        frameMap.put("accesses", accesses);
//                        existingList.add(frameMap);
//                    }
//
////__________
