# link_checker/urls.py
from django.urls import path
from .views import check_url

urlpatterns = [
    path("check-url/", check_url, name="check_url"),
]
