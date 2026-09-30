package es.mrdino.decoswap.util;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;

public final class AtomicFiles {
  private AtomicFiles() {}

  public static void write(Path target, byte[] data) throws IOException {
    Files.createDirectories(target.getParent());
    Path temporary =
        target.resolveSibling(target.getFileName() + ".tmp-" + java.util.UUID.randomUUID());
    try {
      Files.write(temporary, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        channel.force(true);
      }
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
