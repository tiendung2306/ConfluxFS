@echo off
echo 🚀 Building CRDT File System with JDK 24...

echo 📋 Checking Java version...
java -version

echo 📋 Checking Maven version...
mvn -version

echo 🔨 Building project...
cd be
mvn clean compile

if %errorlevel% equ 0 (
    echo ✅ Build successful!
    echo 📦 Creating JAR file...
    mvn package -DskipTests
    
    if %errorlevel% equ 0 (
        echo ✅ JAR created successfully!
        echo 📁 JAR location: target/crdt-file-system-1.0.0.jar
    ) else (
        echo ❌ JAR creation failed!
        exit /b 1
    )
) else (
    echo ❌ Build failed!
    exit /b 1
)

echo 🎉 Build completed successfully!
pause
