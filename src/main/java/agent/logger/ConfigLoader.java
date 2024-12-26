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
    private static final String OUTPUT_FILE_PATH_FIELD = "output.file.path";

    private Set<String> targetPackages = Set.of("org/eclipse/edc");
    private String outputFilePath = null;

    private final Map<String, Consumer<String>> propertyHandlers = Map.ofEntries(
        Map.entry(TARGET_PACKAGES_FIELD, this::setTargetPackages),
        Map.entry(OUTPUT_FILE_PATH_FIELD, this::setOutputFilePath)
    );


    public ConfigLoader() {
        loadFromFile();
        loadFromSystemProperties();
    }


    // Load values from a properties file
    private void loadFromFile() {
        String configFilePath = System.getProperty(CONFIG_FILE_FIELD);

        if (configFilePath != null && !configFilePath.isBlank()) {
            try (FileInputStream input = new FileInputStream(configFilePath)) {
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


    public Set<String> getTargetPackages() {
        return this.targetPackages;
    }

    public String getOutputFilePath() {
        return this.outputFilePath;
    }

    private void setTargetPackages(String property) {
        Set<String> packages = new HashSet<>();

        for (String pkg : property.split(",")) {
            packages.add(pkg.replace(".", "/"));
        }

        this.targetPackages = Set.copyOf(packages);
    }

    private void setOutputFilePath(String property) {
        this.outputFilePath = property;
    }

    public Boolean hasOutputFile() {
        return this.outputFilePath != null && !this.outputFilePath.isBlank();
    }

    private String formatProperty(String property, Object value) {
        return String.format("%s = %s", property, value);
    }
    
    @Override
    public String toString() {
        return String.join("\n",
            formatProperty(TARGET_PACKAGES_FIELD, targetPackages),
            formatProperty(OUTPUT_FILE_PATH_FIELD, outputFilePath)
        );
    }
    
    public void printConfig() {
        System.out.println(this);
    }
}
