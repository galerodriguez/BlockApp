# BlockApp

Firewall por app (sin root) + bloqueo de apps con PIN o huella, con horarios
y protección contra desinstalación.

## Descargar el APK

GitHub Actions compila el APK en cada cambio que se sube a `main`. Última versión:
https://github.com/galerodriguez/BlockApp/releases/latest/download/BlockApp.apk

Después, la propia app avisa cuando hay versión nueva: tarjeta **Actualización disponible ›
Actualizar ahora** (la primera vez pide permitir instalar apps desde BlockApp).

Si Google Play Protect bloquea la instalación: Play Store › tu foto › Play Protect › ⚙ ›
desactiva «Analizar apps con Play Protect», instala y vuelve a activarlo.

## Compilar en local (opcional)

### Opción A – Android Studio
1. Abre esta carpeta con *File > Open* y espera a que sincronice Gradle.
2. *Build > Build App Bundle(s) / APK(s) > Build APK(s)*.
3. El APK queda en `app\build\outputs\apk\debug\app-debug.apk`.

### Opción B – Línea de comandos (Windows)
Requiere tener Android Studio instalado al menos una vez (por el SDK y el JDK).
```bat
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
echo sdk.dir=C\:\\Users\\%USERNAME%\\AppData\\Local\\Android\\Sdk> local.properties
gradlew.bat assembleDebug
```

> Si la carpeta está en OneDrive, pausa la sincronización mientras compilas.

## Primer uso
1. Instala el APK, elige **Proteger este móvil** y crea el **PIN parental**.
2. **Android 13+**: *Ajustes > Apps > BlockApp > ⋮ > Permitir ajustes restringidos*.
3. Pulsa **Activar** en «Bloqueo con PIN» y activa *BlockApp – Bloqueo de apps*.
4. Enciende el **Firewall** y acepta el permiso de VPN.
5. Activa **Impedir desinstalación**.
6. En la lista marca **Sin red**, **PIN** o **Límite** en cada app. Pon **PIN** también a *Ajustes*.

## Funciones
- **Firewall** sin root (VPN local que descarta el tráfico de las apps elegidas).
- **Bloqueo con PIN parental / huella**, re-bloqueo configurable y al apagar la pantalla.
- **Control remoto** desde el móvil del padre (misma app, modo «Controlar el móvil de un hijo»).
- **Horario**: los bloqueos solo se aplican en las horas y días elegidos
  (admite horarios nocturnos, ej. 22:00–07:00).
- **Límite de uso diario** por app (15 min a 3 h). Al agotarse, la app se bloquea
  hasta medianoche; con el PIN o la huella se conceden 15 min extra.
  Solo cuenta el tiempo con la app en pantalla y el móvil encendido.
- **Anti-desinstalación** con Administrador de dispositivo.
- 30 s de espera tras 5 PIN fallidos y registro de intentos.
- PIN con hash PBKDF2 y ajustes cifrados.

## Control desde el móvil del padre
1. En el móvil del hijo: BlockApp › PIN parental › **Vincular con el móvil del padre**. Aparece un código.
2. En el móvil del padre: instala el mismo APK, elige **Controlar el móvil de un hijo**,
   crea un PIN para ese móvil y escribe el código.
3. Desde el móvil del padre puedes cambiar *Sin red*, *PIN*, *Límite*, el horario y el
   re-bloqueo, dar +15/+30 min y ver el uso de hoy y los intentos fallidos.
   Los cambios llegan en 1–2 minutos (el móvil del hijo necesita internet y el
   «Bloqueo con PIN» activado).

Los mensajes viajan por [ntfy.sh](https://ntfy.sh) cifrados con AES-GCM; la clave solo
está en los dos móviles (va dentro del código de vinculación). «Código nuevo» o
«Desvincular» en el móvil del hijo cortan el acceso del padre anterior.

## Apps duplicadas (Dual Messenger, apps duales)
BlockApp detecta las copias (por ejemplo el segundo WhatsApp de Samsung).
- **PIN** y **límite diario** se aplican también a la copia; el tiempo de las dos se suma.
- **Sin red no puede quitarle internet a la copia**: la VPN de Android solo cubre al usuario
  principal y la copia vive en otro perfil. Sin root no hay forma de filtrarla. Si hace falta,
  borra la copia (Samsung: *Ajustes › Funciones avanzadas › Dual Messenger*) y pon PIN a *Ajustes*.

## Consejos
- *Ajustes > Red > VPN > BlockApp*: activa **VPN siempre activa**.
- Excluye BlockApp del ahorro de batería (en Xiaomi, también *Inicio automático*).
- Solo puede haber una VPN activa en el móvil a la vez.
