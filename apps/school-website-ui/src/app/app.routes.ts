import { Routes } from '@angular/router';
import { SiteShellComponent } from './layout/site-shell.component';
import { HomePageComponent } from './pages/home-page.component';
import { CmsPageComponent } from './pages/cms-page.component';
import { NewsListPageComponent } from './pages/news-list-page.component';
import { NewsDetailPageComponent } from './pages/news-detail-page.component';
import { GalleryPageComponent } from './pages/gallery-page.component';
import { EventsPageComponent } from './pages/events-page.component';
import { AdmissionHubPageComponent } from './pages/admission-hub-page.component';
import { AdmissionApplyPageComponent } from './pages/admission-apply-page.component';
import { BlogListPageComponent } from './pages/blog-list-page.component';
import { BlogDetailPageComponent } from './pages/blog-detail-page.component';
import { ContactPageComponent } from './pages/contact-page.component';
import { AlumniListPageComponent } from './pages/alumni-list-page.component';
import { AlumniDetailPageComponent } from './pages/alumni-detail-page.component';

export const routes: Routes = [
  {
    path: '',
    component: SiteShellComponent,
    children: [
      { path: '', component: HomePageComponent },
      { path: 'contact', component: ContactPageComponent },
      { path: 'admission', component: AdmissionHubPageComponent },
      { path: 'admission/apply', component: AdmissionApplyPageComponent },
      { path: 'blog', component: BlogListPageComponent },
      { path: 'blog/:slug', component: BlogDetailPageComponent },
      { path: 'alumni', component: AlumniListPageComponent },
      { path: 'alumni/:slug', component: AlumniDetailPageComponent },
      { path: 'news', component: NewsListPageComponent },
      { path: 'news/:slug', component: NewsDetailPageComponent },
      { path: 'gallery', component: GalleryPageComponent },
      { path: 'events', component: EventsPageComponent },
      { path: ':slug', component: CmsPageComponent },
    ],
  },
];
