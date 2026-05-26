# Astraf (HR GPS Logger)

Личный велокомпьютер для Android: запись поездки с пульсом и GPS, карта, статистика, экспорт. Android 16+.

## Возможности

- **Поездка** — карта (MapLibre + OSM), метрики в реальном времени, пауза/возобновление/завершение, автопауза по скорости, экспорт GPS debug JSON.
- **BLE-пульсометр** — Heart Rate Service; автоподключение к заданному MAC (см. `BleHeartRateClient.TARGET_DEVICE_ADDRESS`).
- **GPS** — Fused Location Provider, фильтрация и сегменты трека, набор высоты, скорость.
- **Фон** — foreground service с уведомлением; восстановление после сбоя и перезагрузки; экран поверх блокировки.
- **Треки** — список поездок, детали, графики, GPX, переименование/удаление.
- **Статистика** — периоды, heatmap, рекорды, карта активности.
- **Strava** — OAuth и загрузка завершённых поездок (TCX).
- **Настройки** — BLE, батарея, crash-логи, Strava.

## Платформа

| Параметр | Значение |
|----------|----------|
| minSdk / targetSdk | 36 (Android 16) |
| compileSdk | 36 |
| ABI (release) | `arm64-v8a` |

Старые версии Android и прочие производители не поддерживаются.

Проверки:
```bash
gradlew.bat testDebugUnitTest
gradlew.bat :app:lintDebug
```

## Ручная проверка на устройстве

1. Установите APK на **Pixel 8+ с Android 16+** (`adb install -r app/build/outputs/apk/release/app-release.apk`).
2. При первом запуске выдайте разрешения (геолокация, Bluetooth, уведомления).
3. **Настройки** → **Подключить** — пульсометр в режиме BLE; дождитесь статуса «Пульсометр готов» (MAC должен совпадать с `TARGET_DEVICE_ADDRESS` в коде).
4. Вкладка **Поездка** → **Начать запись** → дождитесь GPS; в уведомлении — «Идёт запись».
5. При необходимости: **Отключить оптимизацию батареи** в настройках.
6. Сверните приложение / выключите экран на 30–60 с — запись и уведомление должны продолжаться.
7. Завершите поездку с экрана **Поездка** или через уведомление.
8. **Треки** — новая запись в списке; **Статистика** — обновлённые агрегаты.

CSV в приватном каталоге приложения:

- путь: `/data/data/com.astraf.hrgpslogger/files/`
- файл: `hr_gps_<timestamp>.csv`
- заголовок: `gps_timestamp,segment_id,latitude,longitude,accuracy_m,derived_speed_kmh,altitude,bpm`

## Strava

1. Приложение на [Strava API Settings](https://www.strava.com/settings/api).
2. **Authorization Callback Domain**: `localhost` (redirect: `astraf://localhost/strava-auth`).
3. **Настройки → Привязать Strava** — Client ID и Secret вводятся на устройстве, не в APK.
4. **Треки** или карточка поездки → отправка в Strava.

## Ограничения

- Пульсометр с BLE Heart Rate Service (`0x180D`); MAC прошит в `BleHeartRateClient`.
- Явная остановка — завершение поездки в UI или из уведомления; пауза сохраняет сессию.
- После kill процесса или reboot активная запись восстанавливается, если сессия была сохранена.
- Strava и тайлы OSM требуют сети.

## Стек

- Kotlin, Jetpack Compose, Material 3
- Play Services Location, MapLibre (Vulkan)
- OkHttp (OSM-тайлы для превью маршрутов)

