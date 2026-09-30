package es.mrdino.decoswap.selection;

import es.mrdino.decoswap.decoration.Anchor;
import es.mrdino.decoswap.util.BlockKey;
import java.util.*;
import org.bukkit.Location;

public final class PlayerSelection {
  private final LinkedHashSet<BlockKey> blocks = new LinkedHashSet<>();
  private final LinkedHashSet<UUID> entities = new LinkedHashSet<>();
  private final Deque<State> undo = new ArrayDeque<>();
  private SelectionMode mode = SelectionMode.OBJECT;
  private Location pos1, pos2;
  private Anchor anchor;

  public Set<BlockKey> blocks() {
    return Collections.unmodifiableSet(blocks);
  }

  public Set<UUID> entities() {
    return Collections.unmodifiableSet(entities);
  }

  public SelectionMode mode() {
    return mode;
  }

  public void mode(SelectionMode m) {
    mode = m;
  }

  public Location pos1() {
    return pos1 == null ? null : pos1.clone();
  }

  public Location pos2() {
    return pos2 == null ? null : pos2.clone();
  }

  public Anchor anchor() {
    return anchor;
  }

  public boolean toggle(BlockKey key) {
    remember();
    if (!blocks.remove(key)) {
      blocks.add(key);
      return true;
    }
    return false;
  }

  public boolean toggle(UUID id) {
    remember();
    if (!entities.remove(id)) {
      entities.add(id);
      return true;
    }
    return false;
  }

  public boolean add(BlockKey key, int limit) {
    if (blocks.size() >= limit) return false;
    return blocks.add(key);
  }

  public boolean add(UUID id, int limit) {
    if (entities.size() >= limit) return false;
    return entities.add(id);
  }

  public void beginBulk() {
    remember();
  }

  public void clear() {
    remember();
    blocks.clear();
    entities.clear();
    anchor = null;
  }

  public int clearBlocks() {
    remember();
    int n = blocks.size();
    blocks.clear();
    return n;
  }

  public int clearEntities() {
    remember();
    int n = entities.size();
    entities.clear();
    return n;
  }

  public int removeEntitiesByType(java.util.function.Predicate<UUID> predicate) {
    remember();
    int before = entities.size();
    entities.removeIf(predicate);
    return before - entities.size();
  }

  public int removeBlocks(java.util.function.Predicate<BlockKey> predicate) {
    remember();
    int before = blocks.size();
    blocks.removeIf(predicate);
    return before - blocks.size();
  }

  public void invertBlocks(Collection<BlockKey> candidates, int limit) {
    remember();
    for (BlockKey key : candidates) {
      if (!blocks.remove(key) && blocks.size() < limit) blocks.add(key);
    }
  }

  public void position(int index, Location location) {
    if (index == 1) pos1 = location.clone();
    else pos2 = location.clone();
  }

  public void anchor(Anchor anchor) {
    this.anchor = anchor;
  }

  public void clearAnchor() {
    anchor = null;
  }

  public boolean undo() {
    State s = undo.pollFirst();
    if (s == null) return false;
    blocks.clear();
    blocks.addAll(s.blocks);
    entities.clear();
    entities.addAll(s.entities);
    return true;
  }

  private void remember() {
    undo.addFirst(new State(Set.copyOf(blocks), Set.copyOf(entities)));
    while (undo.size() > 20) undo.removeLast();
  }

  private record State(Set<BlockKey> blocks, Set<UUID> entities) {}
}
