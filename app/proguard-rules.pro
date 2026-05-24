# LiteRT-LM uses native libraries + reflection-driven @Tool dispatch.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.ryan.smalltalk.tools.SmallTalkToolSet { *; }
-keepclassmembers class com.ryan.smalltalk.tools.SmallTalkToolSet { *; }
-keepattributes *Annotation*,Signature
