# Build status Test 1c

I regression check statici per il probe Accessibility passano.

La compilazione Android completa non è stata eseguita in questo ambiente perché il Gradle wrapper tenta di scaricare Gradle da `services.gradle.org`, ma l'ambiente non ha accesso di rete (`UnknownHostException`).
