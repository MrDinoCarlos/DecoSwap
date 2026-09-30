package es.mrdino.decoswap.compat;

public interface ServerCapabilities {
  String adapterName();

  boolean supportedVersion();

  String minecraftVersion();
}
