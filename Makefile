# Atalhos do projeto. `make` sozinho lista os comandos.
#
# Variáveis que dá pra trocar na linha de comando:
#   make android ADB_SERIAL=192.168.1.13:5555   (quando há mais de um celular conectado)
#   make apk JAVA_HOME=/caminho/do/jdk         (o padrão é o JDK do Android Studio)

export PATH := $(HOME)/.cargo/bin:$(PATH)

# O Java do sistema costuma ser só o JRE; o Gradle precisa de um JDK completo.
JAVA_HOME ?= $(firstword $(wildcard /opt/android-studio/jbr $(HOME)/android-studio/jbr /usr/lib/jvm/java-21-openjdk))
export JAVA_HOME

ADB := adb $(if $(ADB_SERIAL),-s $(ADB_SERIAL))
APK := android/app/build/outputs/apk/debug/app-debug.apk
APP_ID := com.deivide.webtoontranslator

.DEFAULT_GOAL := help
.PHONY: help dev appimage test apk android

help: ## Lista os comandos
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  make %-10s %s\n", $$1, $$2}'

dev: ## Desktop em modo desenvolvimento
	cd src-tauri && cargo tauri dev

appimage: ## Gera o AppImage do desktop (src-tauri/target/release/bundle/appimage)
	cd src-tauri && cargo tauri build --bundles appimage

test: ## Roda os testes do núcleo e do desktop
	cd core && cargo test --release --lib
	cd src-tauri && cargo test --release --lib

apk: ## Gera o APK do Android (inclui compilar o Rust pra Android)
	cd android && ./gradlew assembleDebug
	@echo "APK: $(APK)"

android: apk ## Gera o APK, instala no celular conectado (adb) e abre o app
	$(ADB) install -r $(APK)
	$(ADB) shell am start -n $(APP_ID)/.MainActivity
