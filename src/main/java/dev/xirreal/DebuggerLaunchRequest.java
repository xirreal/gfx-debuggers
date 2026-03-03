package dev.xirreal;

import java.util.List;

public final class DebuggerLaunchRequest {

   public final DebuggerPicker.DebuggerSelection selection;
   public final String platform;
   public final List<String> extraArgs;

   public DebuggerLaunchRequest(DebuggerPicker.DebuggerSelection selection, String platform, List<String> extraArgs) {
      this.selection = selection;
      this.platform = platform;
      this.extraArgs = extraArgs != null ? extraArgs : List.of();
   }

   public DebuggerLaunchRequest(DebuggerPicker.DebuggerSelection selection) {
      this(selection, null, List.of());
   }
}
