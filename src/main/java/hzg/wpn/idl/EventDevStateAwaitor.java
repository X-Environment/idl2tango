/*
 * The main contributor to this project is Institute of Materials Research,
 * Helmholtz-Zentrum Geesthacht,
 * Germany.
 *
 * This project is a contribution of the Helmholtz Association Centres and
 * Technische Universitaet Muenchen to the ESS Design Update Phase.
 *
 * The project's funding reference is FKZ05E11CG1.
 *
 * Copyright (c) 2012. Institute of Materials Research,
 * Helmholtz-Zentrum Geesthacht,
 * Germany.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */

package hzg.wpn.idl;

import org.apache.log4j.Logger;
import wpn.hdri.tango.data.EnumDevState;
import wpn.hdri.tango.proxy.*;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 05.06.12
 */
public class EventDevStateAwaitor extends TangoDevStateAwaitor {

    private final Object internalLock = new Object();
    public EventDevStateAwaitor(TangoProxyWrapper proxy, Logger log, TangoProxyExceptionHandler handler) {
        super(log, proxy, handler);
    }

    @Override
    public void waitUntil(EnumDevState targetState) {
        try {
            EnumDevState crtState = getProxy().readAttribute(STATE);
            setCrtDevState(crtState);

            int eventId = subscribeToStateChange();
            synchronized (internalLock){
                while(!targetStateReached(targetState)){
                    internalLock.wait();
                }
            }
            getProxy().unsubscribeEvent(eventId);
        } catch (TangoProxyException devFailed) {
            throw getHandler().handle(devFailed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLog().error("Awaiting interrupted.");
            throw getHandler().handle(error != null ? error : e);
        }
    }

    @Override
    public void waitUntilNot(EnumDevState targetState){
        try {
            EnumDevState crtState = getProxy().readAttribute(STATE);
            setCrtDevState(crtState);

            int eventId = subscribeToStateChange();
            synchronized (internalLock){
                while(targetStateReached(targetState)){
                    internalLock.wait();
                }
            }
            getProxy().unsubscribeEvent(eventId);
        } catch (TangoProxyException devFailed) {
            throw getHandler().handle(devFailed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLog().error("Awaiting interrupted.");
            throw getHandler().handle(error != null ? error : e);
        }
    }

    private volatile Throwable error = null;
    private int subscribeToStateChange() throws TangoProxyException {
        final Thread mainThread = Thread.currentThread();
        return getProxy().subscribeEvent(STATE, TangoEvent.CHANGE,new TangoEventCallback<EnumDevState>() {
            @Override
            public void onEvent(EventData<EnumDevState> eventData) {
                EnumDevState crtState = eventData.getValue();
                if(crtState == null) return;
                synchronized (internalLock){
                    setCrtDevState(crtState);
                    internalLock.notifyAll();
                }
            }

            @Override
            public void onError(Throwable e) {
                error = e;
                //does not work in IDL
//                getLog().error(e.getMessage(),e);
                mainThread.interrupt();
            }
        });
    }

}
