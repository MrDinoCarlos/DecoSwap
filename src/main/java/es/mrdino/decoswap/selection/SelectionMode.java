package es.mrdino.decoswap.selection;

public enum SelectionMode {
  OBJECT,
  REGION,
  ANCHOR;

  public SelectionMode next() {
    return values()[(ordinal() + 1) % values().length];
  }
}
