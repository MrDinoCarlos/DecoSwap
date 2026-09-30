package es.mrdino.decoswap.transaction;

import es.mrdino.decoswap.decoration.BlockRecord;
import es.mrdino.decoswap.storage.BinaryCodec;
import es.mrdino.decoswap.util.AtomicFiles;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class SnapshotStore {
  private final Path snapshots, transactions, archive;
  private final BinaryCodec codec = new BinaryCodec();

  public SnapshotStore(JavaPlugin plugin) {
    Path root = plugin.getDataFolder().toPath();
    snapshots = root.resolve("snapshots");
    transactions = root.resolve("transactions");
    archive = root.resolve("backups/completed-snapshots");
  }

  public Path snapshotPath(UUID id) {
    return snapshots.resolve(id + ".dsnap");
  }

  public Path expectedPath(UUID id) {
    return transactions.resolve(id + ".expected");
  }

  public void writeSnapshot(UUID id, List<BlockRecord> records) throws IOException {
    AtomicFiles.write(snapshotPath(id), codec.encodeSnapshot(records));
  }

  public List<BlockRecord> readSnapshot(UUID id) throws IOException {
    return codec.decodeSnapshot(Files.readAllBytes(snapshotPath(id)));
  }

  public void writeExpected(UUID id, List<BlockRecord> records) throws IOException {
    AtomicFiles.write(expectedPath(id), codec.encodeSnapshot(records));
  }

  public List<BlockRecord> readExpected(UUID id) throws IOException {
    return codec.decodeSnapshot(Files.readAllBytes(expectedPath(id)));
  }

  public void complete(UUID id, boolean archiveSnapshot) throws IOException {
    Files.deleteIfExists(expectedPath(id));
    Path source = snapshotPath(id);
    if (archiveSnapshot && Files.exists(source)) {
      Files.createDirectories(archive);
      Files.move(source, archive.resolve(id + ".dsnap"), StandardCopyOption.REPLACE_EXISTING);
    } else Files.deleteIfExists(source);
  }
}
