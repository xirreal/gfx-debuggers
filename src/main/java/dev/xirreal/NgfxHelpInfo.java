package dev.xirreal;

import java.util.List;

public final class NgfxHelpInfo {

   public final List<String> platforms;
   public final List<NgfxOption> gpuTraceOptions;

   public NgfxHelpInfo(List<String> platforms, List<NgfxOption> gpuTraceOptions) {
      this.platforms = platforms;
      this.gpuTraceOptions = gpuTraceOptions;
   }
}
