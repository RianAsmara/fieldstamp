# kotlinx.serialization: keep generated serializers for template/report configuration JSON.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class id.asmaraputra.fieldstamp.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class id.asmaraputra.fieldstamp.**$$serializer { *; }
