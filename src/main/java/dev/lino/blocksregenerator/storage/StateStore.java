package dev.lino.blocksregenerator.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class StateStore implements AutoCloseable {
    private final Path path;
    private final Clock clock;
    private final Consumer<String> errors;
    private final StateCodec codec = new StateCodec();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("blocks-regenerator-storage").factory());
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

    public StateStore(Path path, Clock clock, Consumer<String> errors) {
        this.path = path;
        this.clock = clock;
        this.errors = errors;
    }

    public SavedState load() throws IOException {
        if (!Files.exists(path)) {
            return SavedState.empty();
        }
        if (Files.size(path) > 128L * 1024 * 1024) {
            throw new IOException("Plugin data exceeds the 128 MiB safety limit");
        }
        try {
            return codec.decode(Files.readString(path), clock.millis());
        } catch (RuntimeException error) {
            throw new IOException("Cannot read plugin data; the original file was preserved", error);
        }
    }

    public synchronized CompletableFuture<Void> save(SavedState state) {
        long now = clock.millis();
        tail = tail.handle((ignored, error) -> null).thenRunAsync(() -> {
            try {
                write(codec.encode(state, now));
            } catch (IOException | RuntimeException error) {
                errors.accept("Cannot save plugin data: " + error.getMessage());
                throw new IllegalStateException("Cannot save plugin data", error);
            }
        }, writer);
        return tail;
    }

    public void saveBlocking(SavedState state) {
        save(state).join();
    }

    public synchronized void flush() {
        tail.join();
    }

    private void write(String text) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "state-", ".tmp");
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            if (Files.exists(path)) {
                Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void close() {
        try {
            flush();
        } finally {
            writer.shutdown();
        }
    }
}
