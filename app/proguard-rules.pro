# Quartz Launcher. Activities and services in the manifest are kept automatically.

# Lottie (weather icons) pulls in Okio 1.x, which refers to compile-time-only annotations that aren't shipped
# with apps. They're never used when the app runs, so R8 can ignore them.
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
