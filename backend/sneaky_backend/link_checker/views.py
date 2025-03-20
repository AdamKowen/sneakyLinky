import time
import requests
import json
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
    """
    Process the VirusTotal report to classify the URL as:
      - 'malicious' if any scanner flagged it (via detected flag or result value),
      - 'safe' if at least 5 scanners mark it as safe,
      - 'suspicious' otherwise.

    The returned details include counts for each distinct result,
    with the last three keys always being 'safe', 'unrated', and 'total'.
    """
    from collections import OrderedDict

    # Hard-coded threshold for safe classifications
    SAFE_THRESHOLD = 5

    scans = report.get("scans", {})
    total_scanners = len(scans)

    # If there are no scan results, we can't classify the URL.
    if total_scanners == 0:
        return {
            "status": "suspicious",
            "message": "No scan data available for this URL.",
            "permalink": report.get("permalink", ""),
            "details": {},
        }

    # Initialize counters
    safe_count = 0
    unrated_count = 0
    # We'll use other_counts to track any result types that are not 'clean site' or 'unrated site'.
    other_counts = {}

    # Define result values that indicate malicious behavior if present,
    # even if detected is not set.
    malicious_indicators = {"phishing site", "malicious"}

    # Process each scanner's result.
    for scanner, details in scans.items():
        result = details.get("result")
        # If the scanner explicitly flagged this URL as malicious.
        if details.get("detected", False) or (result in malicious_indicators):
            # We count these as 'malicious'
            other_counts["malicious"] = other_counts.get("malicious", 0) + 1
        else:
            # Standard responses
            if result == "clean site":
                safe_count += 1
            elif result == "unrated site":
                unrated_count += 1
            else:
                # Count any unexpected result types.
                other_counts[result] = other_counts.get(result, 0) + 1

    # Build the details OrderedDict with extra result types first.
    details_dict = OrderedDict()
    for key, count in other_counts.items():
        details_dict[key] = count
    # Append the three fixed keys at the end.
    details_dict["safe"] = safe_count
    details_dict["unrated"] = unrated_count
    details_dict["total"] = total_scanners

    # Classification logic
    # Malicious if any scanner flagged it explicitly.
    if other_counts.get("malicious", 0) > 0:
        return {
            "status": "malicious",
            "message": f"{other_counts.get('malicious')} scanner(s) flagged this URL as malicious.",
            "permalink": report.get("permalink", ""),
            "details": details_dict,
        }
    # Safe if enough scanners mark it as safe.
    if safe_count >= SAFE_THRESHOLD:
        return {
            "status": "safe",
            "message": f"{safe_count} out of {total_scanners} scanner(s) marked this URL as safe.",
            "permalink": report.get("permalink", ""),
            "details": details_dict,
        }
    # Otherwise, we classify it as suspicious.
    return {
        "status": "suspicious",
        "message": f"Only {safe_count} out of {total_scanners} scanner(s) marked this URL as safe, which is below our safe threshold of {SAFE_THRESHOLD}.",
        "permalink": report.get("permalink", ""),
        "details": details_dict,
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
        # print(json.dumps(report_data, indent=4))
    except requests.RequestException as e:
        return Response({"error": str(e)}, status=500)

    # 4. Process and return the result
    if report_data:
        result = process_report(report_data)
        return Response(result)
    else:
        return Response({"status": "pending", "message": "Scan is still pending; please try again later."}, status=202)
