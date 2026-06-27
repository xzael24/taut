@echo off
REM ============================================================
REM start-dev.bat — Windows dev startup script for TAUT
REM Starts the full TAUT stack (PostgreSQL, Adminer, Backend)
REM with hot-reload mode for backend development.
REM ============================================================

echo ============================================
echo  TAUT Development Environment
echo ============================================
echo.
echo Starting PostgreSQL 16, Adminer, and Backend...
echo Backend will be at: http://localhost:8080
echo Adminer will be at: http://localhost:8081
echo PostgreSQL at: localhost:5432 (taut/taut)
echo.

REM Check if Docker is available
where docker >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: Docker not found. Please install Docker Desktop from:
    echo https://docs.docker.com/get-docker/
    pause
    exit /b 1
)

REM Check if docker-compose.yml exists at project root
if not exist "%~dp0..\docker-compose.yml" (
    echo ERROR: docker-compose.yml not found in project root.
    echo Expected at: %~dp0..\docker-compose.yml
    pause
    exit /b 1
)

REM Start the dev stack
echo Pulling images and starting containers...
cd /d "%~dp0.."
docker compose up -d

if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to start Docker containers.
    pause
    exit /b 1
)

echo.
echo ============================================
echo  Services started successfully!
echo.
echo  Backend API:  http://localhost:8080
echo  Health check: http://localhost:8080/health
echo  Adminer DB:   http://localhost:8081
echo  PostgreSQL:   localhost:5432
echo ============================================
echo.
echo Press any key to stop all services...
pause

echo Stopping services...
docker compose down
echo Done.
