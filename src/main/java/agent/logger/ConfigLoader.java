package agent.logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

public class ConfigLoader {
    private static final String CONFIG_FILE_FIELD = "config.file";
    private static final String TARGET_PACKAGES_FIELD = "target.packages";

    private Set<String> targetPackages = Set.of("org/eclipse/edc");

    private final Map<String, Consumer<String>> propertyHandlers = Map.ofEntries(
        Map.entry(TARGET_PACKAGES_FIELD, this::loadTargetPackages)
    );
    
    public ConfigLoader() {
        loadFromFile();
        loadFromSystemProperties();
    }

    // Load values from a properties file
    private void loadFromFile() {
        String filePath = System.getProperty(CONFIG_FILE_FIELD);

        if (filePath != null && !filePath.isEmpty()) {
            try (FileInputStream input = new FileInputStream(filePath)) {
                Properties fileProperties = new Properties();
                fileProperties.load(input);

                loadProperties(fileProperties);
            } 
            catch (IOException e) {
                System.err.println("Error loading properties file: " + e.getMessage());
            }
        }
    }

    // Load values from system properties
    private void loadFromSystemProperties() {
        Properties systemProperties = System.getProperties();
        loadProperties(systemProperties);
    }

    private void loadProperties(Properties properties) {
        for (String key : propertyHandlers.keySet()) {
            if (properties.containsKey(key)) {
                String value = properties.getProperty(key);
                propertyHandlers.get(key).accept(value); // Delegate to the handler
            }
        }
    }

    private void loadTargetPackages(String packagesProperty) {
        Set<String> packages = new HashSet<>();

        for (String pkg : packagesProperty.split(",")) {
            packages.add(pkg.replace(".", "/"));
        }

        this.targetPackages = Set.copyOf(packages);
    }

    public Set<String> getTargetPackages() {
        return this.targetPackages;
    }

    public void printConfig() {
        System.out.println(TARGET_PACKAGES_FIELD + " = " + targetPackages);
    }
}
