import time
import requests
from django.core.validators import URLValidator
from django.core.exceptions import ValidationError
from rest_framework.decorators import api_view
from rest_framework.response import Response
from django.conf import settings


def submit_scan(url):
    """Submit the URL scan to VirusTotal."""
    scan_endpoint = "https://www.virustotal.com/vtapi/v2/url/scan"
    payload = {
        "apikey": settings.VIRUSTOTAL_API_KEY,
        "url": url,
    }
    response = requests.post(scan_endpoint, data=payload)
    response.raise_for_status()
    return response.json()


def poll_report(url, attempts=None, intervals=None):
    """
    Poll VirusTotal for a report.

    :param url: The URL to check.
    :param attempts: A list of sleep durations between polling attempts.
    :param intervals: List of durations (in seconds) to wait before each poll.
    :return: The report data if available, else None.
    """
    if attempts is None:
        # Default: poll after 0.2 seconds, then after an additional 0.8 seconds.
        attempts = [0.2, 0.8]

    report_endpoint = "https://www.virustotal.com/vtapi/v2/url/report"
    for delay in attempts:
        time.sleep(delay)
        response = requests.get(
            report_endpoint,
            params={
                "apikey": settings.VIRUSTOTAL_API_KEY,
                "resource": url,
            },
        )
        response.raise_for_status()
        report = response.json()
        if report.get("response_code") == 1:
            return report
    return None


def process_report(report):
    """Process the VirusTotal report to return a simplified result."""
    positives = report.get("positives", 0)
    total = report.get("total", 0)
    permalink = report.get("permalink", "")

    if positives > 0:
        return {
            "status": "unsafe",
            "message": f"Link flagged by {positives} out of {total} scanners.",
            "permalink": permalink,
        }
    else:
        return {
            "status": "safe",
            "message": "No threats detected.",
        }


@api_view(["POST"])
def check_url(request):
    # 1. Extract and validate URL
    url = request.data.get("url")
    if not url:
        return Response({"error": "URL is required"}, status=400)

    validator = URLValidator()
    try:
        validator(url)
    except ValidationError:
        return Response({"error": "Invalid URL provided"}, status=400)

    # 2. Submit scan request
    try:
        submit_scan(url)
    except requests.RequestException as e:
        return Response({"error": str(e)}, status=500)

    # 3. Poll for the report
    try:
        report_data = poll_report(url)
    except requests.RequestException as e:
        return Response({"error": str(e)}, status=500)

    # 4. Process and return the result
    if report_data:
        result = process_report(report_data)
        return Response(result)
    else:
        return Response({"status": "pending", "message": "Scan is still pending; please try again later."}, status=202)
