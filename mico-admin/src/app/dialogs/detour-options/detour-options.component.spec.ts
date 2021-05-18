import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { DetourOptionsComponent } from './detour-options.component';

describe('DetourOptionsComponent', () => {
  let component: DetourOptionsComponent;
  let fixture: ComponentFixture<DetourOptionsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [DetourOptionsComponent]
    })
      .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DetourOptionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
