package dev.xirreal;

import java.util.List;

public final class NgfxOption {

   public final String flag;
   public final boolean takesValue;
   public final String defaultValue;
   public final String description;
   public final boolean deprecated;
   public final boolean booleanArg;
   public final List<String> choices;

   public NgfxOption(String flag, boolean takesValue, String defaultValue, String description, boolean deprecated, boolean booleanArg, List<String> choices) {
      this.flag = flag;
      this.takesValue = takesValue;
      this.defaultValue = defaultValue;
      this.description = description;
      this.deprecated = deprecated;
      this.booleanArg = booleanArg;
      this.choices = choices;
   }
}
