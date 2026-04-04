# Blu-ray ISO Streamer

Java-based Blu-ray ISO network streaming service.

## Features
- Parse Blu-ray ISO file structure (UDF format)
- HTTP file index listing
- Stream/play files directly from network ISO
- Support HTTP Range requests (video seeking)
- 302 redirect handling for network URLs

## Usage

```bash
# Build
javac -d out src/com/iso/*.java

# Run (port optional, default 8080)
java -cp out com.iso.Main [port]
```

## Endpoints
- `/files` - List all files in ISO
- `/stream?file=<filepath>` - Stream file for video player
- `/download?file=<filepath>` - Download file

## Configuration
Edit `url.txt` in workspace to set ISO network URL.

## License
MIT