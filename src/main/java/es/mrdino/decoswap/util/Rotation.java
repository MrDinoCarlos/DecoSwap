package es.mrdino.decoswap.util;

public enum Rotation {
  NONE(0),
  CLOCKWISE_90(90),
  CLOCKWISE_180(180),
  COUNTERCLOCKWISE_90(270);
  private final int degrees;

  Rotation(int degrees) {
    this.degrees = degrees;
  }

  public int degrees() {
    return degrees;
  }

  public static Rotation ofDegrees(int degrees) {
    int normalized = Math.floorMod(degrees, 360);
    return switch (normalized) {
      case 0 -> NONE;
      case 90 -> CLOCKWISE_90;
      case 180 -> CLOCKWISE_180;
      case 270 -> COUNTERCLOCKWISE_90;
      default -> throw new IllegalArgumentException("Rotation must be 0, 90, 180 or 270");
    };
  }

  public double[] rotate(double x, double z) {
    return switch (this) {
      case NONE -> new double[] {x, z};
      case CLOCKWISE_90 -> new double[] {-z, x};
      case CLOCKWISE_180 -> new double[] {-x, -z};
      case COUNTERCLOCKWISE_90 -> new double[] {z, -x};
    };
  }
}
