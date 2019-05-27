package net.helix.hlx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.nio.file.SensitivityWatchEventModifier;
import net.helix.hlx.service.CallableRequest;
import net.helix.hlx.service.dto.AbstractResponse;
import net.helix.hlx.service.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sun.jmx.mbeanserver.Util.cast;
import static java.nio.file.StandardWatchEventKinds.*;

public class HXI {

    private static final Logger log = LoggerFactory.getLogger(HXI.class);
    private static final int MAX_TREE_DEPTH = 2;

    private final Gson gson = new GsonBuilder().create();
    private final ScriptEngine scriptEngine = (new ScriptEngineManager()).getEngineByName("JavaScript");
    private final Map<String, Map<String, CallableRequest<AbstractResponse>>> hxiAPI = new HashMap<>();
    private final Map<String, Map<String, Runnable>> hxiLifetime = new HashMap<>();
    private final Map<WatchKey, Path> watchKeys = new HashMap<>();
    private final Map<Path, Long> loadedLastTime = new HashMap<>();

    private WatchService watcher;
    private Thread dirWatchThread;
    private Path rootPath;

    private boolean shutdown = false;
    private final Helix helix;

    public HXI() {
        helix = null;
    }

    public HXI(Helix helix) {
        this.helix = helix;
    }

    public void init(String rootDir) throws IOException {
        if(rootDir.length() > 0) {
            watcher = FileSystems.getDefault().newWatchService();
            this.rootPath = Paths.get(rootDir);
            if(this.rootPath.toFile().exists() || this.rootPath.toFile().mkdir()) {
                registerRecursive(this.rootPath);
                dirWatchThread = (new Thread(this::processWatchEvents));
                dirWatchThread.start();
            }
        }
    }

    private void registerRecursive(final Path root) throws IOException {
        Files.walkFileTree(root, EnumSet.allOf(FileVisitOption.class), MAX_TREE_DEPTH, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path modulePath, BasicFileAttributes attrs) {
                watch(modulePath);
                if (!modulePath.equals(rootPath)) {
                    loadModule(modulePath);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void processWatchEvents() {
        while(!shutdown) {
            WatchKey key = null;
            try {
                key = watcher.poll(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                log.error("Watcher interrupted: ", e);
            }
            if (key == null) {
                continue;
            }
            WatchKey finalKey = key;
            key.pollEvents().forEach(watchEvent -> {
                WatchEvent<Path> pathEvent = cast(watchEvent);
                HxiEvent hxiEvent = HxiEvent.fromName(watchEvent.kind().name());
                Path watchedPath = watchKeys.get(finalKey);
                if (watchedPath != null) {
                    handleModulePathEvent(watchedPath, hxiEvent, watchedPath.resolve(pathEvent.context()));
                }
            });
            key.reset();
        }
    }

    private String getModuleName(Path modulePath, boolean checkIfIsDir) {
        return rootPath.relativize(!checkIfIsDir || Files.isDirectory(modulePath) ? modulePath : modulePath.getParent()).toString();
    }

    private Path getRealPath(Path currentPath) {
        if (Files.isDirectory(currentPath.getParent()) && !currentPath.getParent().equals(rootPath)) {
            return currentPath.getParent();
        } else {
            return currentPath;
        }
    }

    private void handleModulePathEvent(Path watchedPath, HxiEvent hxiEvent, Path changedPath) {
        if (!watchedPath.equals(rootPath) && Files.isDirectory(changedPath)) { // we are only interested in dir changes in tree depth level 2
            return;
        }
        handlePathEvent(hxiEvent, changedPath);
    }

    private void handlePathEvent(HxiEvent hxiEvent, Path changedPath) {
        switch(hxiEvent) {
            case CREATE_MODULE:
                if (checkOs() == OsVariants.Unix) {
                    watch(changedPath);
                    loadModule(changedPath);
                }
                break;
            case MODIFY_MODULE:
                Long lastModification = loadedLastTime.get(getRealPath(changedPath));
                if (lastModification == null || Instant.now().toEpochMilli() - lastModification > 50L) {
                    if (hxiLifetime.containsKey(getModuleName(changedPath, true))) {
                        unloadModule(changedPath);
                    }
                    loadedLastTime.put(getRealPath(changedPath), Instant.now().toEpochMilli());
                    loadModule(getRealPath(changedPath));
                }
                break;
            case DELETE_MODULE:
                Path realPath = getRealPath(changedPath);
                unwatch(realPath);
                if (hxiLifetime.containsKey(getModuleName(realPath, false))) {
                    unloadModule(changedPath);
                }
                break;
            default:
        }
    }

    private static OsVariants checkOs() {
        String os = System.getProperty("os.name");
        if (os.startsWith("Windows")) {
            return OsVariants.Windows;
        } else {
            return OsVariants.Unix;
        }
    }

    private void watch(Path dir) {
        try {
            WatchKey watchKey = dir.register(watcher, new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY}, SensitivityWatchEventModifier.HIGH);
            watchKeys.put(watchKey, dir);
        } catch (IOException e) {
            log.error("Could not create watcher for path '{}'.", dir);
        }
    }

    private void unwatch(Path dir) {
        // TODO: Get watchkey for 'dir' in an optimized way
        Optional<WatchKey> dirKey = watchKeys.keySet().stream().filter(watchKey -> watchKeys.get(watchKey).equals(dir)).findFirst();
        if (dirKey.isPresent()) {
            watchKeys.remove(dirKey.get());
            dirKey.get().cancel();
        }
    }

    private Path getPackagePath(Path modulePath) {
        return modulePath.resolve("package.json");
    }

    public AbstractResponse processCommand(final String command, Map<String, Object> request) {
        if(command == null || command.isEmpty()) {
            return ErrorResponse.create("Command can not be null or empty");
        }

        Pattern pattern = Pattern.compile("^(.*)\\.(.*)$");
        Matcher matcher = pattern.matcher(command);

        if (matcher.find()) {
            Map<String, CallableRequest<AbstractResponse>> hxiMap = hxiAPI.get(matcher.group(1));
            if (hxiMap != null && hxiMap.containsKey(matcher.group(2))) {
                return hxiMap.get(matcher.group(2)).call(request);
            }
        }
        return ErrorResponse.create("Command [" + command + "] is unknown");
    }

    private void loadModule(Path modulePath) {
        log.info("Searching: {}", modulePath);
        Path packageJsonPath = getPackagePath(modulePath);
        if (!Files.exists(packageJsonPath)) {
            log.info("No package.json found in {}", modulePath);
            return;
        }
        Map packageJson;
        Reader packageJsonReader;
        try {
            packageJsonReader = new FileReader(packageJsonPath.toFile());
            packageJson = gson.fromJson(packageJsonReader, Map.class);
        } catch (FileNotFoundException e) {
            log.error("Could not load {}", packageJsonPath);
            return;
        }
        try {
            packageJsonReader.close();
        } catch (IOException e) {
            log.error("Could not close file {}", packageJsonPath);
        }
        if(packageJson != null && packageJson.get("main") != null) {
            log.info("Loading module: {}", getModuleName(modulePath, true));
            Path pathToMain = Paths.get(modulePath.toString(), (String) packageJson.get("main"));
            attach(pathToMain, getModuleName(modulePath, true));
        } else {
            log.info("No start script found");
        }
    }

    private void unloadModule(Path moduleNamePath) {
        log.debug("Unloading module: {}", moduleNamePath);
        Path realPath = getRealPath(moduleNamePath);
        String moduleName = getModuleName(realPath, false);
        detach(moduleName);
        hxiAPI.remove(moduleName);
    }

    private void attach(Path pathToMain, String moduleName) {
        Reader hxiModuleReader;
        try {
            hxiModuleReader = new FileReader(pathToMain.toFile());
        } catch (FileNotFoundException e) {
            log.error("Could not load {}", pathToMain);
            return;
        }
        log.info("Starting script: {}", pathToMain);
        Map<String, CallableRequest<AbstractResponse>> hxiMap = new HashMap<>();
        Map<String, Runnable> startStop = new HashMap<>();

        Bindings bindings = scriptEngine.createBindings();
        bindings.put("API", hxiMap);
        bindings.put("HXICycle", startStop);
        bindings.put("HELIX", helix);

        hxiAPI.put(moduleName, hxiMap);
        hxiLifetime.put(moduleName, startStop);
        try {
            scriptEngine.eval(hxiModuleReader, bindings);
        } catch (ScriptException e) {
            log.error("Script error", e);
        }
        try {
            hxiModuleReader.close();
        } catch (IOException e) {
            log.error("Could not close {}", pathToMain);
        }
    }

    private void detach(String moduleName) {
        Map<String, Runnable> hxiMap = hxiLifetime.get(moduleName);
        if(hxiMap != null) {
            Runnable stop = hxiMap.get("shutdown");
            if (stop != null) {
                stop.run();
            }
        }
        hxiLifetime.remove(moduleName);
    }

    /**
     * Cleans up the environment, shutdown the dir watcher thread and wait till all running api calls are completed.
     * @throws InterruptedException if directory watching thread was unexpected interrupted.
     */
    public void shutdown() throws InterruptedException {
        if(dirWatchThread != null) {
            shutdown = true;
            dirWatchThread.join();
            hxiAPI.keySet().forEach(this::detach);
            hxiAPI.clear();
            hxiLifetime.clear();
        }
    }
}
