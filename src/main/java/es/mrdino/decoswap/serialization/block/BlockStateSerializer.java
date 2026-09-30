package es.mrdino.decoswap.serialization.block;

import java.util.Map;
import org.bukkit.block.BlockState;

public interface BlockStateSerializer<T extends BlockState> {
  boolean supports(BlockState state);

  void capture(T state, Map<String, String> properties, Map<Integer, byte[]> items)
      throws Exception;

  void apply(T state, Map<String, String> properties, Map<Integer, byte[]> items) throws Exception;
}
