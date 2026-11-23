import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

interface Identity { id: number; name: string; email: string; role: 'CLAIMANT' | 'OFFICER' }
interface Evidence { id: number; type: string; reference: string; verificationStatus: string }
interface Activity { id: number; actor: string; action: string; note: string | null; occurredAt: string }
interface Claim { id: number; claimantId: number; assignedOfficerId: number | null; incidentDate: string; vehicleRegistration: string; description: string; claimedAmount: number; provisionalExposure: number; status: string; version: number; evidence: Evidence[]; activities: Activity[] }
interface Finding { type: string; explanation: string; relatedClaimId: number | null }
interface Triage { priority: string; analyzedAt: string; ruleVersion: string; findings: Finding[] }
interface Dashboard { totalClaims: number; countsByStatus: Record<string, number>; outstandingProvisionalExposure: number }
interface Csrf { token: string; headerName: string }
interface ClaimPage { content: Claim[]; number: number; totalPages: number; totalElements: number }

@Component({ selector: 'app-root', imports: [CommonModule, FormsModule], templateUrl: './app.html', styleUrl: './app.css' })
export class App {
  identity: Identity | null = null;
  claims: Claim[] = [];
  page = 0;
  totalPages = 0;
  totalClaims = 0;
  selected: Claim | null = null;
  triage: Triage | null = null;
  summary: Dashboard | null = null;
  screen: 'claims' | 'new' = 'claims';
  loginEmail = 'claimant@example.test';
  password = 'demo-password';
  busy = false;
  error = '';
  notice = '';
  actionNote = '';
  evidenceNote = '';
  extraPhoto = '';
  extraQuote = '';
  form = { incidentDate: '', vehicleRegistration: '', description: '', claimedAmount: null as number | null, photo: '', quote: '' };
  private authorization = '';
  private csrf: Csrf | null = null;
  constructor(private cdr: ChangeDetectorRef) {}
  async api<T>(path: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = { Authorization: this.authorization };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      if (!this.csrf) throw new Error('Session expired. Please sign in again.');
      headers[this.csrf.headerName] = this.csrf.token;
    }
    const response = await fetch('/api/v1' + path, { method: body === undefined ? 'GET' : 'POST', headers, credentials: 'same-origin', body: body === undefined ? undefined : JSON.stringify(body) });
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      throw new Error(response.status === 401 ? 'Sign-in failed. Check your email and password.' : response.status === 409 ? 'This claim changed or the action is no longer allowed. Refresh the claim and try again.' : data.error || `Request failed (${response.status}).`);
    }
    return response.json() as Promise<T>;
  }
  async run(fn: () => Promise<void>) {
    this.error = ''; this.notice = ''; this.busy = true; this.cdr.markForCheck();
    try { await fn(); } catch (e) { this.error = e instanceof Error ? e.message : 'Something went wrong.'; }
    finally { this.busy = false; this.cdr.markForCheck(); }
  }
  async login() {
    await this.run(async () => {
      this.authorization = 'Basic ' + btoa(`${this.loginEmail.trim()}:${this.password}`);
      const identity = await this.api<Identity>('/me');
      this.csrf = await this.api<Csrf>('/csrf');
      this.identity = identity; this.password = ''; await this.load();
    });
    if (!this.identity) { this.authorization = ''; this.csrf = null; }
  }
  logout() { this.identity = null; this.authorization = ''; this.csrf = null; this.claims = []; this.page = 0; this.totalPages = 0; this.totalClaims = 0; this.selected = null; this.triage = null; this.summary = null; this.screen = 'claims'; this.password = ''; this.notice = ''; this.error = ''; }
  async load() {
    const page = await this.api<ClaimPage>(`/claims?page=${this.page}&size=20`);
    this.claims = page.content; this.totalPages = page.totalPages; this.totalClaims = page.totalElements;
    this.summary = this.identity?.role === 'OFFICER' ? await this.api<Dashboard>('/dashboard/summary') : null;
  }
  refresh() { void this.run(async () => { await this.load(); if (this.selected) await this.openClaim(this.selected.id); }); }
  changePage(page: number) { if (page < 0 || page >= this.totalPages) return; this.page = page; void this.run(() => this.load()); }
  async openClaim(id: number) {
    this.selected = await this.api<Claim>(`/claims/${id}`);
    this.triage = null;
    try { this.triage = await this.api<Triage>(`/claims/${id}/triage`); }
    catch (e) { if (!(e instanceof Error) || !e.message.includes('No triage result')) throw e; }
    this.screen = 'claims'; this.actionNote = ''; this.evidenceNote = ''; this.extraPhoto = ''; this.extraQuote = '';
  }
  select(id: number) { void this.run(() => this.openClaim(id)); }
  evidence(photo: string, quote: string) {
    return [...(photo.trim() ? [{ type: 'DAMAGE_PHOTO', reference: photo.trim() }] : []), ...(quote.trim() ? [{ type: 'REPAIR_QUOTATION', reference: quote.trim() }] : [])];
  }
  submit() { void this.run(async () => {
    const claim = await this.api<Claim>('/claims', { incidentDate: this.form.incidentDate, vehicleRegistration: this.form.vehicleRegistration, description: this.form.description, claimedAmount: this.form.claimedAmount, evidence: this.evidence(this.form.photo, this.form.quote) });
    this.form = { incidentDate: '', vehicleRegistration: '', description: '', claimedAmount: null, photo: '', quote: '' };
    this.page = 0; await this.load(); await this.openClaim(claim.id); this.notice = `Claim #${claim.id} submitted.`;
  }); }
  act(action: 'assign' | 'request-info' | 'additional-info' | 'approve' | 'reject' | 'triage') { void this.run(async () => {
    if (!this.selected || !this.identity) return;
    const id = this.selected.id; const version = this.selected.version;
    if (action === 'triage') await this.api(`/claims/${id}/triage`, {});
    else {
      const body = action === 'assign' ? { version, officerId: this.identity.id, note: this.actionNote } : action === 'additional-info' ? { version, note: this.actionNote, evidence: this.evidence(this.extraPhoto, this.extraQuote) } : action === 'approve' || action === 'reject' ? { version, note: this.actionNote, decision: action === 'approve' ? 'APPROVED' : 'REJECTED' } : { version, note: this.actionNote };
      const path = action === 'approve' || action === 'reject' ? 'decision' : action;
      await this.api(`/claims/${id}/${path}`, body);
    }
    await this.load(); await this.openClaim(id); this.notice = action === 'triage' ? 'Triage refreshed.' : 'Claim updated.';
  }); }
  reviewEvidence(evidenceId: number, status: 'VERIFIED' | 'REJECTED') { void this.run(async () => {
    if (!this.selected) return;
    const id = this.selected.id;
    await this.api(`/claims/${id}/evidence/${evidenceId}/review`, { version: this.selected.version, status, note: this.evidenceNote });
    await this.load(); await this.openClaim(id); this.notice = 'Evidence review recorded.';
  }); }
  label(value: string) { return value.replaceAll('_', ' ').toLowerCase(); }
}
