# Sydney Loader (старый, MC 1.21.4, v3.0.0) — отчёт о реверсе
# Дата: 28.09.2026. Инструменты: javassist offline-patch + runtime hooks (см. hook.log)

## Архитектура лоадера
- Fabric mod (preLaunch entrypoint), чистая Java, обфускация: строки в пулах,
  invokedynamic-диспетчер D.i (аналог протектора нового лоадера), control flow flattening.
- D.i = диспетчер рефлексии: a(long)->индекс пула, b(long)->Class.forName,
  c(long)->Field, d(long)->Method. Пул 1109 строк, двухступенчатый XOR-дешифр.

## Авторизация (ПОЛНОСТЬЮ ВЗЛОМАНА)
- Файл ключа: %USERPROFILE%\.sydney\authorization
- Формат: base64url( IV[16] || AES-256-CBC-PKCS5(plaintext, K, IV) )
- K = PBKDF2-HMAC-SHA256(
    password = "dW0wBAlrg62G0eaJpeDvpJjH2jenDyMKhW6tN5uaHhW3IHvWr5zYnkrwiRH5znjV",  # хардкод
    salt     = "JZ1NNWMFKN47jg9mCkAQlIwKDTcNy3Kb",                                   # хардкод
    100000 итераций, 256 бит)
- K = 05ef8193ceaefb7172bb04e9af2533a987e491dd1cb99f61e908e90e7f06d9f7
- plaintext = содержимое лицензии (проверяется только !isEmpty на этапе лоадера)
- ГЕНЕРАЦИЯ СВОИХ КЛЮЧЕЙ: keygen.ps1 — работает, лоадер принимает.

## HWID
- SHA-256( os.name + os.arch + os.version + PROCESSOR_IDENTIFIER +
  PROCESSOR_ARCHITECTURE + PROCESSOR_ARCHITEW6432 + NUMBER_OF_PROCESSORS + MAC )
- Анти-VM: blacklist MAC-префиксов 00:1C:42(Parallels) 00:0C:29/00:05:69/00:1C:14/00:50:56(VMware)
  08:00:27(VirtualBox) 52:54:00(QEMU)
- Анти-дебаг: JVM args (agentlib, Xdebug, Xnoagent, Xrunjdwp, javaagent, jmxremote,
  XBootclasspath, verbose, Dproxy*, DtrustStore*)

## Сетевой протокол (для получения клиента 3.0.0)
- POST https://www.sydneyclient.net/api/v1/loader/request
- Body: {"authorization":"<ChaCha20-encrypted blob>"}
- ChaCha20 key: 04df20987d2cab6a2ebb4d71872055cec9a5fc962587f1258cfaa8e1cf52f8a4
  (из shared secret "SENndfFVaFEuBOiIbPeBkQOLuRnZEd2R4XfvNi2xwoZumH3bVayqN5TDda32ozNL")
- nonce: случайный 12B на запрос
- Внутри authorization: ключ из .sydney + HWID hash (2 слоя шифрования)

## СТАТУС: тупик по серверу
- Ответ сервера на /api/v1/loader/request: HTTP 404 — эндпоинт удалён.
- Старый домен sydneyclient.xyz — DNS не резолвится.
- => Клиент 3.0.0 неоткуда скачать ДАЖЕ с валидным ключом.

## Файлы
- keygen.ps1                          — генератор authorization-файлов
- sydney-loader-hooked.jar            — лоадер с логирующими хуками
- hook.log                            — полный трейс расшифровок/вызовов
- fl/fabric-loader-patched.jar        — fabric-loader с isDevelopmentEnvironment=false
