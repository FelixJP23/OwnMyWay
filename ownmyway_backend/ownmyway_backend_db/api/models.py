from django.db import models

class TestConnection(models.Model):
    message = models.CharField(max_length=100)