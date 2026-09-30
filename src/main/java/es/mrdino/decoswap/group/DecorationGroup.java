package es.mrdino.decoswap.group;

import java.util.*;

public final class DecorationGroup {
  private final String id;
  private String displayName;
  private final List<String> members;
  private String iconTexture;

  public DecorationGroup(String id, String displayName, List<String> members) {
    this(id, displayName, members, null);
  }

  public DecorationGroup(String id, String displayName, List<String> members, String iconTexture) {
    this.id = id;
    this.displayName = displayName;
    this.members = new ArrayList<>(members);
    this.iconTexture = iconTexture;
  }

  public String id() {
    return id;
  }

  public String displayName() {
    return displayName;
  }

  public List<String> members() {
    return Collections.unmodifiableList(members);
  }

  public String iconTexture() {
    return iconTexture;
  }

  public void iconTexture(String texture) {
    iconTexture = texture;
  }

  public boolean add(String decorationId) {
    if (members.contains(decorationId)) return false;
    return members.add(decorationId);
  }

  public boolean remove(String decorationId) {
    return members.remove(decorationId);
  }
}
