#!/bin/bash
git add .
git commit -m "Auto-update: $(date +'%Y-%m-%d %H:%M:%S')"
git push origin main

# Δημιουργία μοναδικού Tag και GitHub Release με το APK/build
TAG="v1.0.$(date +%s)"
gh release create "$TAG" --title "Automated Build $TAG" --notes "Generated automatically by Grok AI agent."
