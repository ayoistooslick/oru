import os

import uvicorn

if __name__ == "__main__":
    reload = os.environ.get("ENVIRONMENT", "development").lower() not in {"production", "prod"}
    uvicorn.run("app.main:app", host="0.0.0.0", port=int(os.environ.get("PORT", "8000")), reload=reload)
