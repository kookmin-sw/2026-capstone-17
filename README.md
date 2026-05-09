# FOCUS

실시간 스트리밍 중 스트리머와 주변 인물을 구분하여, 비대상 인물의 얼굴을 자동으로 보호하고  
방송 종료 후에는 AI 기반 회고 리포트까지 제공하는 스트리밍 지원 서비스입니다.

## Overview

FOCUS는 라이브 스트리밍 환경에서 발생할 수 있는 초상권 침해 문제를 줄이기 위해 기획된 프로젝트입니다.  
방송 중에는 실시간 얼굴 인식과 선택적 보호 처리를 제공하고,  
방송 종료 후에는 AI 분석을 통해 방송 요약, 하이라이트, 개선 포인트를 제공합니다.

## Key Features

- 실시간 Owner / Other 구분
- 비대상 인물 얼굴 보호 처리
- 얼굴 트래킹 및 재진입 추적
- 방송 종료 후 AI 회고 리포트
- 날짜별 방송 회고록 조회
- 원본 클립 저장 기능

## Tech Stack

### Mobile
- iOS (Swift)
- Android (Kotlin)

### Backend
- Spring Boot
- FastAPI
- Redis

### Vision / AI
- OpenCV
- YuNet
- ArcFace
- 3DMM
- Gemini API

## Repository Structure

- `android/` : Android app
- `fast-api-server/` : AI / analysis server
- `spring/focus-server/` : Spring backend
- `index.html` : GitHub Pages intro page

## Team

- 이지상 — 팀장, AOS 개발
- 이동언 — iOS 개발
- 이제준 — FastAPI
- 민승호 — Spring
- 신윤솔 — OpenCV

## Project Goal

FOCUS는 단순한 얼굴 모자이크 앱이 아니라,  
스트리머가 더 안전하게 방송하고, 방송 이후에는 더 나은 콘텐츠를 준비할 수 있도록 돕는 것을 목표로 합니다.

## GitHub Pages

프로젝트 소개 페이지:  
[FOCUS Intro Page](여기에-깃허브-페이지-링크-넣기)

## Future Work

- SOOPLIVE 연동 확장
- 시청자 수 기반 방송 피크 분석
- 모션캡처 기반 자동 원본 클립 저장
- 방송 회고 리포트 고도화
