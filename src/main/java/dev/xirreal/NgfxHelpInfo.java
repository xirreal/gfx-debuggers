package dev.xirreal;

import java.util.List;

public final class NgfxHelpInfo {

   public final List<String> platforms;
   public final List<String> activities;
   public final List<NgfxOption> gpuTraceOptions;

   public NgfxHelpInfo(List<String> platforms, List<String> activities, List<NgfxOption> gpuTraceOptions) {
      this.platforms = platforms;
      this.activities = activities;
      this.gpuTraceOptions = gpuTraceOptions;
   }

   public String findActivity(String keyword) {
      for (String activity : activities) {
         if (activity.toLowerCase().contains(keyword.toLowerCase())) {
            return activity;
         }
      }
      return null;
   }
}
