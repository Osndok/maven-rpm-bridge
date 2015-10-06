package javax.module.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Created by robert on 2015-10-06 14:11.
 */
class InterfaceCoercion implements InvocationHandler
{

	private final Object              target;
	private final Map<Method, Method> methodMap;

	public
	InterfaceCoercion(Object o, Class iface)
	{
		this.target = o;
		this.methodMap = InterfaceCoercionMapping.given(iface, o.getClass());
	}

	@Override
	public
	Object invoke(Object proxy, Method interfaceMethod, Object[] arguments) throws Throwable
	{
		//System.err.println("invoke: "+target.getClass()+", "+target+", "+interfaceMethod);
		return methodMap.get(interfaceMethod).invoke(target, arguments);
	}
}
