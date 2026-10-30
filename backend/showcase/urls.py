from django.urls import path

from . import views as v

urlpatterns = [
    path('owner/showcase/portfolio/', v.PortfolioCollectionView.as_view(), name='showcase-portfolio'),
    path('owner/showcase/portfolio/reorder/', v.PortfolioReorderView.as_view(), name='showcase-portfolio-reorder'),
    path('owner/showcase/portfolio/<int:pk>/', v.PortfolioDetailView.as_view(), name='showcase-portfolio-detail'),
    path('owner/showcase/certificates/', v.CertificateCollectionView.as_view(), name='showcase-certificates'),
    path('owner/showcase/certificates/reorder/', v.CertificateReorderView.as_view(), name='showcase-certificates-reorder'),
    path('owner/showcase/certificates/<int:pk>/', v.CertificateDetailView.as_view(), name='showcase-certificate-detail'),
    path('client/businesses/<int:business_id>/showcase/', v.ClientShowcaseView.as_view(), name='client-business-showcase'),
]
