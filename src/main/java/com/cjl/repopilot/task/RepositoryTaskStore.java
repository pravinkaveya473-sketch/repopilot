package com.cjl.repopilot.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * File-backed task store with process-level file locking and atomic replacement.
 */
public final class RepositoryTaskStore {

    private static final TypeReference<List<RepositoryTask>> TASK_LIST = new TypeReference<>() {};

    private final Path dataFile;
    private final Path lockFile;
    private final ObjectMapper mapper;

    public RepositoryTaskStore(Path repositoryRoot) {
        Path dataDir = repositoryRoot.toAbsolutePath().normalize().resolve(".repopilot").resolve("tasks");
        this.dataFile = dataDir.resolve("tasks.json");
        this.lockFile = dataDir.resolve("tasks.lock");
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public List<RepositoryTask> findAll() {
        try {
            return withLock(this::readUnsafe).stream()
                    .sorted(Comparator.comparing(RepositoryTask::createdAt).reversed())
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read repository tasks", ex);
        }
    }

    public Optional<RepositoryTask> findById(String id) {
        return findAll().stream().filter(task -> task.id().equals(id)).findFirst();
    }


    public RepositoryTask insert(RepositoryTask task) {
        try {
            return withLock(() -> {
                List<RepositoryTask> tasks = new ArrayList<>(readUnsafe());
                boolean exists = tasks.stream().anyMatch(existing -> existing.id().equals(task.id()));
                if (exists) {
                    throw new IllegalStateException("Repository task already exists: " + task.id());
                }
                tasks.add(task);
                writeUnsafe(tasks);
                return task;
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create repository task", ex);
        }
    }

    public RepositoryTask save(RepositoryTask task) {
        try {
            return withLock(() -> {
                List<RepositoryTask> tasks = new ArrayList<>(readUnsafe());
                int index = -1;
                for (int i = 0; i < tasks.size(); i++) {
                    if (tasks.get(i).id().equals(task.id())) {
                        index = i;
                        break;
                    }
                }
                if (index >= 0) {
                    RepositoryTask current = tasks.get(index);
                    if (task.version() <= current.version()) {
                        throw new IllegalStateException("Stale task version: expected > " + current.version());
                    }
                    tasks.set(index, task);
                } else {
                    tasks.add(task);
                }
                writeUnsafe(tasks);
                return task;
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save repository task", ex);
        }
    }

    private List<RepositoryTask> readUnsafe() throws IOException {
        if (!Files.exists(dataFile) || Files.size(dataFile) == 0) {
            return new ArrayList<>();
        }
        return mapper.readValue(dataFile.toFile(), TASK_LIST);
    }

    private void writeUnsafe(List<RepositoryTask> tasks) throws IOException {
        Files.createDirectories(dataFile.getParent());
        Path temp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        mapper.writeValue(temp.toFile(), tasks);
        try {
            Files.move(temp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temp, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private <T> T withLock(IOSupplier<T> action) throws IOException {
        Files.createDirectories(lockFile.getParent());
        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            return action.get();
        }
    }

    @FunctionalInterface
    private interface IOSupplier<T> {
        T get() throws IOException;
    }
}
