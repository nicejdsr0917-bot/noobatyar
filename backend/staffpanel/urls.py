from django.urls import path

from .views import banners, businesses, customers, dashboard, finance, marketing, moderation, reviews, search, sms

app_name = 'staffpanel'

urlpatterns = [
    path('', dashboard.dashboard, name='dashboard'),
    path('search/', search.search, name='search'),

    path('approvals/', moderation.approvals, name='approvals'),
    path('approvals/bulk/', moderation.approvals_bulk, name='approvals_bulk'),
    path('approvals/<int:pk>/decide/', moderation.approval_decide, name='approval_decide'),
    path('showcase/', moderation.showcase, name='showcase'),
    path('showcase/<str:kind>/bulk/', moderation.showcase_bulk, name='showcase_bulk'),
    path('showcase/<str:kind>/<int:pk>/decide/', moderation.showcase_decide, name='showcase_decide'),

    path('reviews/', reviews.reviews, name='reviews'),
    path('reviews/<int:pk>/visibility/', reviews.review_visibility, name='review_visibility'),
    path('reports/', reviews.reports, name='reports'),
    path('reports/<int:pk>/resolve/', reviews.report_resolve, name='report_resolve'),

    path('customers/', customers.customers, name='customers'),
    path('customers/<int:pk>/', customers.customer_detail, name='customer_detail'),
    path('businesses/', businesses.businesses, name='businesses'),
    path('businesses/<int:pk>/', businesses.business_detail, name='business_detail'),

    path('banners/', banners.banners, name='banners'),
    path('banners/new/', banners.banner_create, name='banner_create'),
    path('banners/link/', banners.banner_link, name='banner_link'),
    path('banners/<int:pk>/toggle/', banners.banner_toggle, name='banner_toggle'),

    path('marketing/', marketing.marketing, name='marketing'),
    path('marketing/<int:pk>/update/', marketing.lead_update, name='lead_update'),
    path('finance/', finance.finance, name='finance'),
    path('sms/', sms.sms, name='sms'),
]
