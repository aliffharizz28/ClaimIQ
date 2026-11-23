import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from './app';

describe('ClaimIQ sign-in', () => {
  afterEach(() => vi.unstubAllGlobals());
  it('shows the claimant workspace after authentication', async () => {
    const fetchMock = vi.fn().mockImplementation(async (url: string) => ({
      ok: true,
      json: async () => url.endsWith('/me') ? { id: 1, name: 'Demo Claimant', role: 'CLAIMANT' } : url.endsWith('/csrf') ? { token: 'token', headerName: 'X-CSRF-TOKEN' } : { content: [], totalPages: 0, totalElements: 0 }
    }));
    vi.stubGlobal('fetch', fetchMock);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.componentInstance.login();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Your claims');
    expect(fixture.nativeElement.textContent).toContain('No claims yet');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});
