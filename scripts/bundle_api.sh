#!/bin/sh
# The host publishes the contents of dist/, so the API has to travel with the
# frontend instead of sitting next to it: api/ becomes the serverless function
# folder, app/ is the package it imports and requirements.txt tells the host
# which Python packages to install.
set -e

rm -rf dist/api dist/app dist/requirements.txt
cp -R api dist/api
cp -R app dist/app
cp requirements.txt dist/requirements.txt

# Byte-code caches are build noise, never publish them.
find dist -name '__pycache__' -type d -prune -exec rm -rf {} +
find dist -name '*.pyc' -delete

echo "bundled API: $(find dist/api dist/app -name '*.py' | wc -l) python files"
