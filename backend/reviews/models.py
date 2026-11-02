from django.db import models
from django.db.models import Q


class Review(models.Model):
    """A customer's rating of a business. One per (business, visitor)."""

    MAX_TEXT = 1000

    business = models.ForeignKey(
        'business.Business', on_delete=models.CASCADE, related_name='reviews',
        verbose_name='کسب‌وکار',
    )
    visitor = models.ForeignKey(
        'visitor.Visitor', on_delete=models.CASCADE, related_name='reviews',
        verbose_name='مشتری',
    )
    # The visitor's latest completed appointment there, when they have one.
    appointment = models.ForeignKey(
        'appointment.Appointment', on_delete=models.SET_NULL, null=True, blank=True,
        related_name='reviews', verbose_name='نوبت',
    )
    rating = models.PositiveSmallIntegerField(verbose_name='امتیاز')
    text = models.TextField(max_length=MAX_TEXT, blank=True, default='', verbose_name='متن')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ثبت')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='آخرین ویرایش')
    is_hidden = models.BooleanField(default=False, db_index=True, verbose_name='مخفی')
    hidden_reason = models.CharField(max_length=255, blank=True, default='', verbose_name='دلیل مخفی‌سازی')

    class Meta:
        verbose_name = 'نظر'
        verbose_name_plural = 'نظرات'
        ordering = ['-created_at', '-id']
        constraints = [
            models.UniqueConstraint(fields=['business', 'visitor'], name='review_one_per_visitor'),
            models.CheckConstraint(
                check=Q(rating__gte=1) & Q(rating__lte=5), name='review_rating_1_5',
            ),
        ]
        indexes = [
            models.Index(fields=['business', 'is_hidden', '-created_at'], name='review_public_list_idx'),
        ]

    def __str__(self):
        return f'{self.rating}★ {self.business_id} by {self.visitor_id}'
