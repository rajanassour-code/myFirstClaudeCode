from flask import Flask, render_template, request
import requests

app = Flask(__name__)

API_KEY = "4ee09ad5b45e40e6a3be40d7996ae2ec"
BASE_URL = "https://newsapi.org/v2"

CATEGORIES = ["business", "entertainment", "general", "health", "science", "sports", "technology"]
COUNTRIES = {
    "us": "United States", "gb": "United Kingdom", "ca": "Canada",
    "au": "Australia", "de": "Germany", "fr": "France", "in": "India",
}


def fetch_top_headlines(category="general", country="us", page=1):
    params = {
        "apiKey": API_KEY,
        "category": category,
        "country": country,
        "pageSize": 12,
        "page": page,
    }
    resp = requests.get(f"{BASE_URL}/top-headlines", params=params, timeout=10)
    return resp.json()


def fetch_search(query, sort_by="publishedAt", page=1):
    params = {
        "apiKey": API_KEY,
        "q": query,
        "sortBy": sort_by,
        "pageSize": 12,
        "page": page,
        "language": "en",
    }
    resp = requests.get(f"{BASE_URL}/everything", params=params, timeout=10)
    return resp.json()


@app.route("/")
def index():
    category = request.args.get("category", "general")
    country = request.args.get("country", "us")
    page = int(request.args.get("page", 1))

    data = fetch_top_headlines(category, country, page)
    articles = data.get("articles", [])
    total = data.get("totalResults", 0)
    total_pages = min((total + 11) // 12, 10)

    return render_template(
        "index.html",
        articles=articles,
        category=category,
        country=country,
        countries=COUNTRIES,
        categories=CATEGORIES,
        page=page,
        total_pages=total_pages,
        total=total,
        error=data.get("message") if data.get("status") != "ok" else None,
    )


@app.route("/search")
def search():
    query = request.args.get("q", "").strip()
    sort_by = request.args.get("sort", "publishedAt")
    page = int(request.args.get("page", 1))

    articles = []
    total = 0
    total_pages = 0
    error = None

    if query:
        data = fetch_search(query, sort_by, page)
        if data.get("status") == "ok":
            articles = data.get("articles", [])
            total = data.get("totalResults", 0)
            total_pages = min((total + 11) // 12, 10)
        else:
            error = data.get("message", "An error occurred.")

    return render_template(
        "search.html",
        articles=articles,
        query=query,
        sort=sort_by,
        page=page,
        total_pages=total_pages,
        total=total,
        error=error,
    )


@app.route("/sources")
def sources():
    category = request.args.get("category", "")
    country = request.args.get("country", "")
    params = {"apiKey": API_KEY}
    if category:
        params["category"] = category
    if country:
        params["country"] = country
    resp = requests.get(f"{BASE_URL}/top-headlines/sources", params=params, timeout=10)
    data = resp.json()
    sources_list = data.get("sources", [])
    return render_template(
        "sources.html",
        sources=sources_list,
        category=category,
        country=country,
        categories=CATEGORIES,
        countries=COUNTRIES,
        error=data.get("message") if data.get("status") != "ok" else None,
    )


if __name__ == "__main__":
    app.run(debug=True, port=5000)
