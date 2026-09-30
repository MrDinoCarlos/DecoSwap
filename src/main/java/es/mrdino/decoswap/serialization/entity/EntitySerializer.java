package es.mrdino.decoswap.serialization.entity;

import java.util.Map;
import org.bukkit.entity.Entity;

public interface EntitySerializer<T extends Entity> {
  boolean supports(Entity entity);

  void capture(T entity, Map<String, String> properties, Map<String, byte[]> items)
      throws Exception;

  void apply(T entity, Map<String, String> properties, Map<String, byte[]> items) throws Exception;
}
