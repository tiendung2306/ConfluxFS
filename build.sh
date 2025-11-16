#!/bin/bash

# Build script for CRDT File System with JDK 24

echo "🚀 Building CRDT File System with JDK 24..."

# Check Java version
echo "📋 Checking Java version..."
java -version

# Check Maven version
echo "📋 Checking Maven version..."
mvn -version

# Clean and build
echo "🔨 Building project..."
cd be
mvn clean compile

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo "📦 Creating JAR file..."
    mvn package -DskipTests
    
    if [ $? -eq 0 ]; then
        echo "✅ JAR created successfully!"
        echo "📁 JAR location: target/crdt-file-system-1.0.0.jar"
    else
        echo "❌ JAR creation failed!"
        exit 1
    fi
else
    echo "❌ Build failed!"
    exit 1
fi

echo "🎉 Build completed successfully!"
